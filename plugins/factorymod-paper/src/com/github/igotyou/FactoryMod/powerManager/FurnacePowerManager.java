package com.github.igotyou.FactoryMod.powerManager;

import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

import com.github.igotyou.FactoryMod.factories.FurnCraftChestFactory;
import com.github.igotyou.FactoryMod.utility.ItemMap;

/**
 * Power manager for a FurnCraftChest factory, which uses a specific item in the
 * furnace of the factory as fuel
 *
 */
public class FurnacePowerManager implements IPowerManager {
	private ItemMap fuel;
	private FurnCraftChestFactory fccf;
	private int powerCounter;
	private int fuelConsumptionIntervall;

	public FurnacePowerManager(FurnCraftChestFactory fccf, ItemMap fuel,
			int fuelConsumptionIntervall) {
		this.fccf = fccf;
		this.fuel = fuel;
		this.fuelConsumptionIntervall = fuelConsumptionIntervall;
	}

	public FurnacePowerManager(ItemMap fuel, int fuelConsumptionIntervall) {
		this.fuel = fuel;
		this.fuelConsumptionIntervall = fuelConsumptionIntervall;
	}

	public void setFactory(FurnCraftChestFactory fccf) {
		this.fccf = fccf;
	}

	public int getPowerCounter() {
		return powerCounter;
	}

	public boolean powerAvailable() {
		FurnaceInventory fi = fccf.getFurnaceInventory();
		ItemMap im = new ItemMap();
		im.addItemStack(fi.getFuel());
		im.addItemStack(fi.getSmelting());
		return im.contains(fuel);
	}

	public int getPowerConsumptionIntervall() {
		return fuelConsumptionIntervall;
	}

	public void increasePowerCounter(int amount) {
		powerCounter += amount;
	}

	public void setPowerCounter(int amount) {
		powerCounter = amount;
	}

	public void consumePower() {
		FurnaceInventory fi = fccf.getFurnaceInventory();
		for (ItemStack is : fuel.getItemStackRepresentation()) {
			fi.removeItem(is);
		}
	}

}
