package com.programmerdan.minecraft.simpleadminhacks.configs;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.framework.SimpleHackConfig;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public final class ModSupportConfig extends SimpleHackConfig {
    // Server info
    public boolean sendServerTags;
    public @NotNull List<@NotNull String> serverTags = List.of();

    // World info
    public boolean sendWorldInfo;

    // Inventories
    public boolean sendInventoryLocation;

    public ModSupportConfig(
        final @NotNull SimpleAdminHacks plugin,
        final @NotNull ConfigurationSection config
    ) {
        super(plugin, config);
    }

    @Override
    protected void wireup(
        final @NotNull ConfigurationSection config
    ) {
        // Server info
        this.sendServerTags = config.getBoolean("send-server-tags", true);
        this.serverTags = config.getList("server-tags", null) instanceof final List<?> tags
            ? tags.stream().limit(255).map(String::valueOf).toList()
            : List.of();

        // World info
        this.sendWorldInfo = config.getBoolean("send-world-info", true);

        // Inventory
        this.sendInventoryLocation = config.getBoolean("send-inventory-location", true);
    }
}
