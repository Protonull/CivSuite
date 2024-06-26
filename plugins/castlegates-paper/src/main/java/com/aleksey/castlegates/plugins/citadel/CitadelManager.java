/**
 * @author Aleksey Terzi
 */

package com.aleksey.castlegates.plugins.citadel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.CitadelPermissionHandler;
import vg.civcraft.mc.citadel.model.Reinforcement;

import com.aleksey.castlegates.CastleGates;
import com.aleksey.castlegates.database.ReinforcementInfo;
import vg.civcraft.mc.citadel.playerstate.AbstractPlayerState;
import vg.civcraft.mc.citadel.playerstate.PlayerStateManager;
import vg.civcraft.mc.citadel.playerstate.ReinforcingState;

public class CitadelManager extends Thread implements ICitadelManager, Runnable {

    private static class UpdatedReinforcement {

        public Reinforcement rein;
        public boolean deleted;

        public UpdatedReinforcement(Reinforcement rein, boolean deleted) {
            this.rein = rein;
            this.deleted = deleted;
        }
    }

    private long _lastExecute = System.currentTimeMillis();
    private AtomicBoolean _kill = new AtomicBoolean(false);
    private AtomicBoolean _run = new AtomicBoolean(false);

    private List<UpdatedReinforcement> _updatedReinforcements = new ArrayList<UpdatedReinforcement>();
    private Queue<UpdatedReinforcement> _localUpdatedReinforcements = new ArrayDeque<UpdatedReinforcement>();

    @Override
    public void init() {
        startThread();
    }

    @Override
    public void close() {
        terminateThread();
    }

    @Override
    public double getMaxRedstoneDistance() {
        return CastleGates.getConfigManager().getMaxRedstoneDistance();
    }

    @Override
    public ICitadel getCitadel(List<Player> players, Location loc) {
        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(loc);

        boolean hasAccess = rein == null;
        boolean useJukeAlert = false;

        if (!hasAccess) {
            var doorsPermission = CitadelPermissionHandler.getDoors();

            if (players != null && players.size() > 0) {
                for (Player player : players) {
                    if (rein.hasPermission(player, doorsPermission) || player.hasPermission("citadel.admin")) {
                        hasAccess = true;
                        break;
                    }
                }
            }

            if (!hasAccess) {
                if (CastleGates.getJukeAlertManager().hasJukeAlertAccess(loc, rein.getGroup().getName())) {
                    hasAccess = true;
                    useJukeAlert = true;
                }
            }
        }

        return new com.aleksey.castlegates.plugins.citadel.Citadel(rein, hasAccess, useJukeAlert);
    }

    @Override
    public boolean isReinforced(Location loc) {
        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(loc);
        return rein != null;
    }

    @Override
    public boolean canBypass(Player player, Location loc) {
        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(loc);

        if (rein == null)
            return true;

        if (player == null)
            return false;

        return rein.hasPermission(player, CitadelPermissionHandler.getBypass())
            || player.hasPermission("citadel.admin");
    }

    @Override
    public boolean canViewInformation(Player player, Location loc) {
        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(loc);

        if (rein == null)
            return true;

        if (player == null)
            return false;

        return rein.hasPermission(player, CitadelPermissionHandler.getInfo())
            || player.hasPermission("citadel.admin");
    }

    @Override
    public ReinforcementInfo removeReinforcement(Location loc) {
        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(loc);

        if (rein == null)
            return null;

        ReinforcementInfo info = new ReinforcementInfo();
        info.CreationTime = rein.getCreationTime();
        info.TypeId = rein.getType().getID();
        info.Health = rein.getHealth();
        info.GroupId = rein.getGroupId();
        info.Insecure = rein.isInsecure();

        synchronized (this._updatedReinforcements) {
            this._updatedReinforcements.add(new UpdatedReinforcement(rein, true));
        }

        return info;
    }

    @Override
    public boolean createReinforcement(ReinforcementInfo info, Location loc) {
        if (info == null)
            return false;

        var type = Citadel.getInstance().getReinforcementTypeManager().getById(info.TypeId);
        var rein = new Reinforcement(loc, type, info.GroupId, info.CreationTime, info.Health, info.Insecure, true);

        synchronized (this._updatedReinforcements) {
            this._updatedReinforcements.add(new UpdatedReinforcement(rein, false));
        }

        return true;
    }

    @Override
    public boolean isReinforcingStateActive(Player player) {
        PlayerStateManager stateManager = Citadel.getInstance().getStateManager();
        AbstractPlayerState currentState = stateManager.getState(player);

        return currentState instanceof ReinforcingState;
    }

    public void startThread() {
        setName("CastleGates CitadelManager Thread");
        setPriority(Thread.MIN_PRIORITY);
        start();

        CastleGates.getPluginLogger().log(Level.INFO, "CitadelManager thread started");
    }

    public void terminateThread() {
        this._kill.set(true);

        while (this._run.get()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        saveData();

        CastleGates.getPluginLogger().log(Level.INFO, "CitadelManager thread stopped");
    }

    @Override
    public void run() {
        this._run.set(true);

        try {
            while (!this.isInterrupted() && !this._kill.get()) {
                try {
                    long timeWait = _lastExecute + CastleGates.getConfigManager().getDataWorkerRate() - System.currentTimeMillis();
                    _lastExecute = System.currentTimeMillis();
                    if (timeWait > 0) {
                        Thread.sleep(timeWait);
                    }

                    saveData();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            this._run.set(false);
        }
    }

    private void saveData() {
        try {
            synchronized (this._updatedReinforcements) {
                if (this._updatedReinforcements.size() == 0) return;

                this._localUpdatedReinforcements.addAll(this._updatedReinforcements);
                this._updatedReinforcements.clear();
            }

            UpdatedReinforcement updated;

            while ((updated = this._localUpdatedReinforcements.poll()) != null) {
                if (updated.deleted)
                    updated.rein.setHealth(-1);
                else
                    Citadel.getInstance().getReinforcementManager().putReinforcement(updated.rein);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
