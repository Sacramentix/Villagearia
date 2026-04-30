package villagearia.ai;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import villagearia.ai.action.Leisure;
import villagearia.ai.action.Routine;
import villagearia.ai.action.Work;
import villagearia.ai.action.interrupt.WaveNearbyNpc;
import villagearia.ai.action.routine.RoutinesAvailable;
import villagearia.ai.schedule.DailyScheduleComponent;

public class HousedNpcEntityInjection {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HousedNpcEntityInjection() {
        super();
    }


    public void overrideIdleStateBehavior(
        InjectHousedNpcAI.InjectHousedNpcAiContext ctx
    ) {
        
        var store = ctx.store();
        var chunk = ctx.chunk();
        var npc = ctx.npc();
        var housedNpc = ctx.housedNpc();
        var tickIndex = ctx.i();
        var dt = ctx.dt();
        var cb = ctx.cb();

        wipeRoleBehavior(npc);
        
        var worldTime = store.getResource(WorldTimeResource.getResourceType());

        var entityRef = chunk.getReferenceTo(tickIndex);

        var transform = chunk.getComponent(tickIndex, TransformComponent.getComponentType());
        var headRotation = chunk.getComponent(tickIndex, HeadRotation.getComponentType());
        if (headRotation == null) return;

        var pos = transform.getPosition();

        // Ensure stack and schedule exist
        var actionStack = cb.ensureAndGetComponent(entityRef, villagearia.ai.action.ActionStackComponent.getComponentType());
        var schedule = cb.ensureAndGetComponent(entityRef, DailyScheduleComponent.getComponentType());
        // npc is already available in the scope

        // Generate initial schedule if empty
        if (schedule.slots.isEmpty()) {
            generateSchedule(schedule, housedNpc);
        }

        // Check for interrupts like Waving
        var waveCtx = new WaveNearbyNpc.WaveNearbyNpcContext(entityRef, housedNpc, npc, worldTime, transform, headRotation, pos, store, cb);
        if (WaveNearbyNpc.behavior(waveCtx)) {
            if (!actionStack.stack.contains("WAVE")) {
                actionStack.stack.push("WAVE");
            }
        } else {
            actionStack.stack.remove("WAVE");
        }

        // If high-priority action is active, block standard schedule evaluation
        if (!actionStack.stack.isEmpty()) {
            var currentAction = actionStack.stack.peek();
            if ("WAVE".equals(currentAction)) {
                return; // Interaction locks them in place
            }
        }

        // Evaluate standard Daily Schedule fallback
        var dayProgress = worldTime != null ? worldTime.getDayProgress() : 0.5f;
        String currentActivity = "LEISURE"; // default
        for (var slot : schedule.slots) {
            if (slot.startProgress < slot.endProgress) {
                if (dayProgress >= slot.startProgress && dayProgress <= slot.endProgress) {
                    currentActivity = slot.activity; break;
                }
            } else {
                // Wraps around midnight 
                if (dayProgress >= slot.startProgress || dayProgress <= slot.endProgress) {
                    currentActivity = slot.activity; break;
                }
            }
        }

        applyScheduledActivity(entityRef, currentActivity, cb);

        // Delegate to active component behavior
        if ("SLEEP".equals(currentActivity) || cb.getComponent(entityRef, Routine.RoutineComponent.getComponentType()) != null) {
            Routine.behavior(new Routine.RoutineContext(entityRef, housedNpc, npc, worldTime, transform, headRotation, pos, store, dt, cb));
        } else if ("WORK".equals(currentActivity)) {
            Work.behavior(new Work.WorkContext(entityRef, housedNpc, npc, worldTime, transform, headRotation, pos, store, dt, cb));
        } else {
            Leisure.behavior(new Leisure.LeisureContext(entityRef, housedNpc, npc, worldTime, transform, headRotation, pos, store, dt, cb));
        }
    }

    private void generateSchedule(DailyScheduleComponent schedule, HousedNpcEntity housedNpc) {
        // There is 7 time range of 2 hours for Work or leisure
        // When work duration goal is meet all work time will be leisure time
        var workLeisureTimeRange = 2.00f; // 2:00
        float goalHhMm = housedNpc.workDurationGoal;
        int goalHours = (int) goalHhMm;
        float goalMins = Math.round((goalHhMm - goalHours) * 100f);
        float goalDecimalHours = goalHours + (goalMins / 60f);

        int amountOfSlotNeededForWork = (int) Math.floor(goalDecimalHours / workLeisureTimeRange) + 1;

        var workLeisureSlots = IntStream.range(0, 7)
            .mapToObj(i -> i < amountOfSlotNeededForWork ? "WORK" : "LEISURE")
            .collect(Collectors.toList());
        Collections.shuffle(workLeisureSlots);

        schedule.addSlotHhMm( 0.00f,  6.00f, "SLEEP");
        schedule.addSlotHhMm( 6.01f,  8.00f, workLeisureSlots.get(0));
        schedule.addSlotHhMm( 8.01f, 10.00f, workLeisureSlots.get(1));
        schedule.addSlotHhMm(10.01f, 12.00f, workLeisureSlots.get(2));
        schedule.addSlotHhMm(12.01f, 13.30f, "LEISURE"); // EAT -> We don't have eat for the moment
        schedule.addSlotHhMm(13.31f, 15.30f, workLeisureSlots.get(3));
        schedule.addSlotHhMm(15.31f, 17.30f, workLeisureSlots.get(4));
        schedule.addSlotHhMm(17.31f, 19.30f, workLeisureSlots.get(5));
        schedule.addSlotHhMm(19.31f, 21.30f, workLeisureSlots.get(6));
        schedule.addSlotHhMm(21.31f, 23.59f, "SLEEP");

    }

    private void applyScheduledActivity(Ref<EntityStore> entityRef, String activity, CommandBuffer<EntityStore> cb) {
        if ("SLEEP".equals(activity)) {
            cb.tryRemoveComponent(entityRef, Work.WorkComponent.getComponentType());
            cb.tryRemoveComponent(entityRef, Leisure.LeisureComponent.getComponentType());
            if (cb.getComponent(entityRef, Routine.RoutineComponent.getComponentType()) == null) {
                cb.addComponent(entityRef, Routine.RoutineComponent.getComponentType(), new Routine.RoutineComponent(RoutinesAvailable.SLEEP_ROUTINE));
            }
        } else if ("WORK".equals(activity)) {
            cb.tryRemoveComponent(entityRef, Routine.RoutineComponent.getComponentType());
            cb.tryRemoveComponent(entityRef, Leisure.LeisureComponent.getComponentType());
        } else if ("LEISURE".equals(activity)) {
            cb.tryRemoveComponent(entityRef, Routine.RoutineComponent.getComponentType());
            cb.tryRemoveComponent(entityRef, Work.WorkComponent.getComponentType());
        }
    }

    public void wipeRoleBehavior(NPCEntity npc) {
        if (npc == null) return;           
        npc.getRole().clearOnceIfNeeded(); // Prevent JSON instructional loop
        npc.getRole().getBodySteering().clear();
        npc.getRole().getHeadSteering().clear();
    }
}
