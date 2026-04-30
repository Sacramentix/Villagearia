package villagearia.ai.schedule;

import java.util.ArrayList;
import java.util.List;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;

import com.hypixel.hytale.codec.builder.BuilderCodec;

public class DailyScheduleComponent implements Component<EntityStore> {
    
    public static class ScheduleSlot {
        public float startProgress;
        public float endProgress;
        public String activity;
        public ScheduleSlot(float s, float e, String a) { startProgress = s; endProgress = e; activity = a; }

        public float getStartTimeHhMm() { return progressToTime(startProgress); }
        public float getEndTimeHhMm() { return progressToTime(endProgress); }
    }

    public List<ScheduleSlot> slots = new ArrayList<>();

    public ScheduleSlot addSlotHhMm(float start, float end, String activity) {
        ScheduleSlot slot = new ScheduleSlot(timeToProgress(start), timeToProgress(end), activity);
        this.slots.add(slot);
        return slot;
    }

    public static float timeToProgress(float timeInHhMm) {
        int hours = (int) timeInHhMm;
        float minutes = Math.round((timeInHhMm - hours) * 100f);
        return (hours + (minutes / 60f)) / 24f;
    }

    public static float progressToTime(float progress) {
        float totalHours = progress * 24f;
        int hours = (int) totalHours;
        int minutes = Math.round((totalHours - hours) * 60f);
        if (minutes == 60) { hours += 1; minutes = 0; }
        return hours + (minutes / 100f);
    }

    // Keep it entirely transient
    public static final BuilderCodec<DailyScheduleComponent> CODEC = BuilderCodec.builder(
        DailyScheduleComponent.class, DailyScheduleComponent::new
    ).build();

    public DailyScheduleComponent() {}

    public static ComponentType<EntityStore, DailyScheduleComponent> getComponentType() {
        return Villagearia.instance().getDailyScheduleComponentType();
    }
    
    @Override
    public DailyScheduleComponent clone() {
        var cloned = new DailyScheduleComponent();
        cloned.slots.addAll(this.slots);
        return cloned;
    }
}
