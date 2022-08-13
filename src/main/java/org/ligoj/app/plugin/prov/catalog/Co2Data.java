/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * CO2 data.
 */
@Setter
public class Co2Data {

	/**
	 * Ignored property
	 */
	@Getter
	private String drop;

	/**
	 * Instance type
	 */
	@Getter
	private String type;

	/**
	 * Base hourly Watt whatever is the load.
	 */
	private double extra;

	/**
	 * Scope 3 hourly gCO2eq.
	 */
	@Getter
	private double scope3;

	/**
	 * Package hourly Watt depending on the load.
	 */
	private double pkgWatt0;
	private double pkgWatt10;
	private double pkgWatt20;
	private double pkgWatt30;
	private double pkgWatt40;
	private double pkgWatt50;
	private double pkgWatt60;
	private double pkgWatt70;
	private double pkgWatt80;
	private double pkgWatt90;
	@Getter
	private double pkgWatt100;

	/**
	 * GPU hourly Watt depending on the load.
	 */
	private double gpuWatt0;
	private double gpuWatt10;
	private double gpuWatt20;
	private double gpuWatt30;
	private double gpuWatt40;
	private double gpuWatt50;
	private double gpuWatt60;
	private double gpuWatt70;
	private double gpuWatt80;
	private double gpuWatt90;
	@Getter
	private double gpuWatt100;

	/**
	 * RAM hourly Watt depending on the load.
	 */
	private double ramWatt0;
	private double ramWatt10;
	private double ramWatt20;
	private double ramWatt30;
	private double ramWatt40;
	private double ramWatt50;
	private double ramWatt60;
	private double ramWatt70;
	private double ramWatt80;
	private double ramWatt90;
	@Getter
	private double ramWatt100;

	@Getter
	private double[] pkgWattArray;

	@Getter
	private double[] gpuWattArray;

	@Getter
	private double[] ramWattArray;

	@Getter
	private double[] wattArray;

	/**
	 * Compute cache values.
	 */
	public void compute() {
		setPkgWattArray(new double[] { pkgWatt0, pkgWatt10, pkgWatt20, pkgWatt30, pkgWatt40, pkgWatt50, pkgWatt60,
				pkgWatt70, pkgWatt80, pkgWatt90 });
		setGpuWattArray(new double[] { gpuWatt0, gpuWatt10, gpuWatt20, gpuWatt30, gpuWatt40, gpuWatt50, gpuWatt60,
				gpuWatt70, gpuWatt80, gpuWatt90 });
		setRamWattArray(new double[] { ramWatt0, ramWatt10, ramWatt20, ramWatt30, ramWatt40, ramWatt50, ramWatt60,
				ramWatt70, ramWatt80, ramWatt90 });
		setWattArray(new double[] { getWatt0(), getWatt10(), getWatt20(), getWatt30(), getWatt40(), getWatt50(),
				getWatt60(), getWatt70(), getWatt80(), getWatt90() });
	}

	public double getWatt0() {
		return ramWatt0 + gpuWatt0 + pkgWatt0 + extra;
	}

	public double getWatt10() {
		return ramWatt10 + gpuWatt10 + pkgWatt10 + extra;
	}

	public double getWatt20() {
		return ramWatt20 + gpuWatt20 + pkgWatt20 + extra;
	}

	public double getWatt30() {
		return ramWatt30 + gpuWatt30 + pkgWatt30 + extra;
	}

	public double getWatt40() {
		return ramWatt40 + gpuWatt40 + pkgWatt40 + extra;
	}

	public double getWatt50() {
		return ramWatt50 + gpuWatt50 + pkgWatt50 + extra;
	}

	public double getWatt60() {
		return ramWatt60 + gpuWatt60 + pkgWatt60 + extra;
	}

	public double getWatt70() {
		return ramWatt70 + gpuWatt70 + pkgWatt70 + extra;
	}

	public double getWatt80() {
		return ramWatt80 + gpuWatt80 + pkgWatt80 + extra;
	}

	public double getWatt90() {
		return ramWatt90 + gpuWatt90 + pkgWatt90 + extra;
	}

	public double getWatt100() {
		return ramWatt100 + gpuWatt100 + pkgWatt100 + extra;
	}

}
