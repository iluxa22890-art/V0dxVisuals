package com.vodx.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client entrypoint declared in fabric.mod.json. */
public final class VodxVisualsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Modules.load();
        Effects.init();
        ClientTickEvents.END_CLIENT_TICK.register(KillAura::tick);
    }
}
