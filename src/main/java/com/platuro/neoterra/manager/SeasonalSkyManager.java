package com.platuro.neoterra.manager;

public class SeasonalSkyManager {

    // Typical Minecraft day: 24000 ticks
    // But we can shift or scale it based on lat + season
    public static final int BASE_DAY_TICKS = 24000;

    /**
     * Computes the sun’s position angle [0..1] for the current day,
     * but altered by latitude and season.
     *
     * @param worldTime    The total world time in ticks
     * @param partialTicks Render interpolation (0..1 in many cases)
     * @param latVal       A latitude factor [0..1], 0=equator, 1=pole
     * @param seasonVal    A season factor [0..1], e.g. 0=spring,0.5=summer,1.0=winter
     * @return A float in [0..1], used to place the sun in the sky
     */
    public static float getCelestialAngle(long worldTime, float partialTicks,
                                          float latVal, float seasonVal) {
        // 1) Base "day fraction" in [0..1]
        // E.g. 0 => midnight, 0.25 => 6:00 AM, 0.5 => noon, 0.75 => 6:00 PM, 1 => next midnight
        // vanilla would do:
        //   float dayFraction = ((float)(worldTime % 24000L) + partialTicks) / 24000.0F;
        float baseTicks = ((worldTime % BASE_DAY_TICKS) + partialTicks);
        float dayFraction = baseTicks / (float) BASE_DAY_TICKS;

        // 2) Shift day length or sunrise time based on latVal/seasonVal
        // Example: near poles (latVal ~1), we might reduce daylight in winter or
        // extend it in summer. We'll do a small approach:
        //
        //   dayLengthFactor in [0.5..1.5], so polar winter might have half the normal day length,
        //   polar summer might have 1.5x normal day length.
        //   The range depends on latVal and seasonVal.
        //   For simplicity, let's say max effect happens at latVal=1, seasonVal=1 => "deep winter".
        float latInfluence = latVal;          // how far from equator
        float seasonInfluence = seasonVal;    // how far into winter vs. summer
        // We'll assume 0 => spring, 0.5 => summer, 1 => winter
        // Just a naive formula for demonstration:
        // In deep winter near the pole, day length might be 50% => dayLengthFactor=0.5
        // In deep summer near the pole, day length might be 150% => dayLengthFactor=1.5
        // At the equator (latVal=0), no seasonal day length change

        float polarDayVar = (seasonInfluence - 0.5f) * 2.0f;
        // now polarDayVar is [-1..+1], negative in spring <0.5, positive in late summer/fall
        // let's clamp it for safety
        if (polarDayVar < -1f) polarDayVar = -1f;
        if (polarDayVar > 1f)  polarDayVar = 1f;

        // dayLengthFactor = 1.0 + latVal * polarDayVar * 0.5
        // => if latVal=1, polarDayVar=+1 => dayLengthFactor=1.5 => 150% day
        // => if latVal=1, polarDayVar=-1 => dayLengthFactor=0.5 => 50% day
        // => if latVal=0 => dayLengthFactor=1.0 => no change
        float dayLengthFactor = 1.0f + latInfluence * (polarDayVar * 0.5f);

        // Now we artificially "stretch" or "shrink" the day fraction
        // If factor=1.5 => day is longer => everything moves slower
        // If factor=0.5 => day is shorter => everything moves faster
        float adjustedDayFraction = (dayFraction * dayLengthFactor) % 1.0f;

        // 3) Optionally shift sunrise/sunset around
        // e.g. in winter, sunrise is later, in summer, sunrise is earlier
        // We'll do a simpler approach: no separate shift, just day length

        // 4) Return the final angle [0..1].
        // MC usage: 0 => midnight, 0.5 => noon
        return adjustedDayFraction;
    }

    /**
     * Example for the moon angle. Could do something similar or just keep it
     * synced if you prefer.
     */
    public static float getMoonAngle(long worldTime, float partialTicks,
                                     float latVal, float seasonVal) {
        // Maybe do the same approach or let the moon be unaffected by seasons
        float base = getCelestialAngle(worldTime, partialTicks, 0f, 0f);
        // or a fancier approach for moon phases
        return base;
    }
}