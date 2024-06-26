/**
 * @author Aleksey Terzi
 */

package com.aleksey.castlegates.plugins.bastion;

import java.util.List;

import com.aleksey.castlegates.plugins.citadel.ICitadel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface IBastionManager {

    void init();

    boolean canUndraw(List<Player> players, List<Block> bridgeBlocks, ICitadel jukeAlertCitadel);
}
