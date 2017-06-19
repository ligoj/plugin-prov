package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.mockito.Mockito;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link TerraformResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class TerraformTest extends AbstractAppTest {

	private int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvQuote.class, ProvStorageType.class,
				ProvInstancePriceType.class, ProvInstance.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
	}

	@Test(expected = BusinessException.class)
	public void getTerraformNotSupported() {
		newResource(null).getTerraform(subscription, "any.tf");
	}

	@Test
	public void getTerraform() throws IOException {
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		((StreamingOutput) newResource(terraforming).getTerraform(subscription, "any.tf").getEntity()).write(new ByteArrayOutputStream());
		Mockito.verify(terraforming).terraform(Mockito.any(OutputStream.class), Mockito.eq(subscription), Mockito.any(QuoteVo.class));
	}

	private TerraformResource newResource(final Terraforming providerResource) {
		final TerraformResource resource = new TerraformResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);

		// Replace the plugin locator
		resource.locator = locator;
		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class)).thenReturn(providerResource);
		return resource;
	}
}
