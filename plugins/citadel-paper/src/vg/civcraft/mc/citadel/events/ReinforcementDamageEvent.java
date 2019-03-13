package vg.civcraft.mc.citadel.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import vg.civcraft.mc.citadel.reinforcement.Reinforcement;

/**
 * Called when a reinforcement is damaged by a player
 *
 */
public class ReinforcementDamageEvent extends ReinforcementEvent {

	private static final HandlerList handlers = new HandlerList();

	private double damageDone;

	public ReinforcementDamageEvent(Player player, Reinforcement rein, double damageDone) {
		super(player, rein);
		this.damageDone = damageDone;
	}

	/**
	 * Gets the total damage done by this damaging event
	 * 
	 * @return Total damage
	 */
	public double getDamageDone() {
		return damageDone;
	}

	/**
	 * Sets the total damage done
	 * 
	 * @param damageDone Damage to do
	 */
	public void setDamageDone(double damageDone) {
		this.damageDone = damageDone;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
