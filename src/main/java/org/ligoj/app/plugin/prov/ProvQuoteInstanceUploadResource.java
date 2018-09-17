/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The instance part of the provisioning from upload.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteInstanceUploadResource {
	private static final String[] DEFAULT_HEADERS = { "name", "cpu", "ram", "os", "disk", "latency", "optimized" };
	private static final String[] ACCEPTED_HEADERS = { "name", "cpu", "ram", "constant", "os", "disk", "latency",
			"optimized", "term", "type", "internet", "maxCost", "minQuantity", "maxQuantity", "maxVariableCost",
			"ephemeral", "location", "usage" };

	@Autowired
	private CsvForBean csvForBean;

	@Autowired
	protected ProvResource resource;

	@Autowired
	private ProvQuoteStorageResource storageResource;

	@Autowired
	private SubscriptionResource subscriptionResource;
	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvQuoteInstanceResource qResource;


	/**
	 * Check column's name validity
	 */
	private void checkHeaders(final String[] expected, final String... columns) {
		for (final String column : columns) {
			if (!ArrayUtils.contains(expected, column.trim())) {
				throw new ValidationJsonException("headers", "invalid-header", column);
			}
		}
	}

	/**
	 * Upload a file of quote in add mode.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the locations from the associated provider.
	 * @param uploadedFile
	 *            Instance entries files to import. Currently support only CSV format.
	 * @param headers
	 *            the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded
	 *            When <code>true</code>, the first line is the headers and the given <code>headers</code> parameter is
	 *            ignored. Otherwise the <code>headers</code> parameter is used.
	 * @param usage
	 *            The optional usage name. When not <code>null</code>, each quote instance will be associated to this
	 *            usage.
	 * @param ramMultiplier
	 *            The multiplier for imported RAM values. Default is 1.
	 * @param encoding
	 *            CSV encoding. Default is UTF-8.
	 * @throws IOException
	 *             When the CSV stream cannot be written.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("{subscription:\\d+}/upload")
	public void upload(@PathParam("subscription") final int subscription,
			@Multipart(value = "csv-file") final InputStream uploadedFile,
			@Multipart(value = "headers", required = false) final String[] headers,
			@Multipart(value = "headers-included", required = false) final boolean headersIncluded,
			@Multipart(value = "usage", required = false) final String usage,
			@Multipart(value = "memoryUnit", required = false) final Integer ramMultiplier,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		subscriptionResource.checkVisible(subscription).getNode().getId();
		final String safeEncoding = ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name());

		// Check headers validity
		final String[] sanitizeColumns;
		final Reader reader;
		if (headersIncluded) {
			// Header at first line
			final String rawFile = IOUtils.toString(uploadedFile, safeEncoding);
			sanitizeColumns = StringUtils.defaultString(new BufferedReader(new StringReader(rawFile)).readLine(), "")
					.replace(',', ';').split(";");
			reader = new StringReader(rawFile);
		} else {
			// Headers are provided separately
			sanitizeColumns = ArrayUtils.isEmpty(headers) ? DEFAULT_HEADERS : headers;
			reader = new InputStreamReader(new SequenceInputStream(new ByteArrayInputStream(
					(StringUtils.chop(ArrayUtils.toString(sanitizeColumns)).substring(1).replace(',', ';') + "\n")
							.getBytes(safeEncoding)),
					uploadedFile), safeEncoding);
		}
		checkHeaders(ACCEPTED_HEADERS, sanitizeColumns);

		// Build entries
		csvForBean.toBean(InstanceUpload.class, reader).stream().filter(Objects::nonNull)
				.forEach(i -> persist(i, subscription, usage, ramMultiplier));
	}

	private void persist(final InstanceUpload upload, final int subscription, String usage,
			final Integer ramMultiplier) {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setCpu(qResource.round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		vo.setEphemeral(upload.isEphemeral());
		vo.setInternet(upload.getInternet());
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setMaxQuantity(Optional.ofNullable(upload.getMaxQuantity()).filter(q -> q > 0).orElse(null));
		vo.setMinQuantity(upload.getMinQuantity());
		vo.setName(upload.getName());
		vo.setLocation(upload.getLocation());
		vo.setUsage(Optional.ofNullable(upload.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription).getName()).orElse(usage));
		vo.setRam(
				ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		vo.setSubscription(subscription);

		// Find the lowest price
		final ProvInstancePrice instancePrice = qResource.validateLookup("instance",
				qResource.lookup(subscription, vo.getCpu(), vo.getRam(), upload.getConstant(), upload.getOs(),
						upload.getType(), upload.isEphemeral(), upload.getLocation(), upload.getUsage()),
				upload.getName());

		vo.setPrice(instancePrice.getId());
		final UpdatedCost newInstance = qResource.create(vo);
		final int qi = newInstance.getId();

		// Storage part
		final Integer size = Optional.ofNullable(upload.getDisk()).map(Double::intValue).orElse(0);
		if (size > 0) {
			// Size is provided, propagate the upload properties
			final QuoteStorageEditionVo svo = new QuoteStorageEditionVo();
			svo.setName(vo.getName());
			svo.setQuoteInstance(qi);
			svo.setSize(size);
			svo.setLatency(upload.getLatency());
			svo.setInstanceCompatible(true);
			svo.setOptimized(upload.getOptimized());
			svo.setLocation(upload.getLocation());

			// Find the nicest storage
			svo.setType(storageResource
					.lookup(subscription, size, upload.getLatency(), qi, upload.getOptimized(), upload.getLocation())
					.stream().findFirst().orElseThrow(() -> new ValidationJsonException("storage", "NotNull"))
					.getPrice().getType().getName());

			// Default the storage name to the instance name
			svo.setSubscription(subscription);
			storageResource.create(svo);
		}

	}
}
