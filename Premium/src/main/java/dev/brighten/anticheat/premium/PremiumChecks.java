package dev.brighten.anticheat.premium;

import cc.funkemunky.api.utils.Init;
import cc.funkemunky.api.utils.MiscUtils;
import cc.funkemunky.api.utils.Priority;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.premium.impl.*;

@Init(priority = Priority.LOWEST)
public class PremiumChecks {

    public PremiumChecks() {
        MiscUtils.printToConsole("&aThanks for purchasing Kauri Ara.");
        Check.register(new VelocityB());
        Check.register(new Reach());
        Check.register(new Motion());
        Check.register(new AimB());
        Check.register(new AutoclickerD());
        Check.register(new AutoclickerE());
        Check.register(new AutoclickerG());
        Check.register(new InventoryA());
        Check.register(new InventoryB());
        Check.register(new InventoryB());
    }
}