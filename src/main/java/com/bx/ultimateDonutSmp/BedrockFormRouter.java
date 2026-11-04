package com.bx.ultimateDonutSmp;

import com.gxlydlyf.geyserdetector.api.GeyserDetectorApi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormDefinition;
import org.geysermc.cumulus.form.impl.FormDefinitions;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Bridges the Cumulus forms used by this plugin to GeyserDetector's proxy transport.
 *
 * GeyserDetector sends the form JSON to the proxy and returns the raw Cumulus
 * response JSON. Keep the original Form so Cumulus can deserialize that response
 * and invoke the result/closed handlers configured by HomeBedrockManager and
 * OrdersBedrockManager.
 */
public final class BedrockFormRouter implements Listener {

    private final UltimateDonutSmp plugin;
    private final ConcurrentMap<UUID, PendingForm> pendingForms = new ConcurrentHashMap<>();
    private volatile boolean registered;

    public BedrockFormRouter(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    /** Registers the one response callback supported by GeyserDetector. */
    public void register() {
        if (registered) {
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        GeyserDetectorApi.registerResponseListener(this::handleResponse);
        registered = true;
    }

    public boolean isBedrock(Player player) {
        return player != null && player.isOnline() && GeyserDetectorApi.isBedrock(player);
    }

    /** Sends a form and retains its handlers until GeyserDetector reports a response. */
    public boolean send(Player player, Form form) {
        if (form == null || !isBedrock(player)) {
            return false;
        }

        FormDefinition<?, ?, ?> definition = FormDefinitions.instance().definitionFor(form);
        if (definition == null) {
            plugin.getLogger().warning("Cannot send unsupported Cumulus form type: "
                    + form.getClass().getName());
            return false;
        }

        UUID playerId = player.getUniqueId();
        PendingForm pending = new PendingForm(player, form, definition);
        PendingForm previous = pendingForms.put(playerId, pending);

        final boolean sent;
        try {
            sent = GeyserDetectorApi.sendForm(player, form);
        } catch (LinkageError | RuntimeException error) {
            restorePrevious(playerId, pending, previous);
            plugin.getLogger().log(Level.WARNING,
                    "Could not send Bedrock form to " + player.getName(), error);
            return false;
        }

        if (!sent) {
            restorePrevious(playerId, pending, previous);
        }
        return sent;
    }

    private void handleResponse(UUID playerId, String responseJson) {
        if (playerId == null) {
            return;
        }

        PendingForm pending = pendingForms.remove(playerId);
        if (pending == null) {
            return;
        }

        Player player = pending.player();
        // The callback arrives from GeyserDetector's plugin-channel thread.
        // Run Cumulus handlers on the player's entity thread for Folia safety.
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }

            try {
                dispatchResponse(pending.definition(), pending.form(), responseJson);
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not process Bedrock form response for " + player.getName(), error);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingForms.remove(event.getPlayer().getUniqueId());
    }

    /** Clears pending forms and releases the global GeyserDetector callback. */
    public void clear() {
        pendingForms.clear();
        if (registered) {
            GeyserDetectorApi.registerResponseListener(null);
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void restorePrevious(UUID playerId, PendingForm current, PendingForm previous) {
        if (previous == null) {
            pendingForms.remove(playerId, current);
        } else {
            pendingForms.replace(playerId, current, previous);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void dispatchResponse(
            FormDefinition definition,
            Form form,
            String responseJson
    ) throws Exception {
        definition.handleFormResponse(form, responseJson);
    }

    private record PendingForm(
            Player player,
            Form form,
            FormDefinition<?, ?, ?> definition
    ) {
    }
}
