package villagearia.ai;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;

public class HousedNpcEntity implements Component<EntityStore> {

    
    public static final BuilderCodec<HousedNpcEntity> CODEC = BuilderCodec.builder(
            HousedNpcEntity.class, HousedNpcEntity::new
        )
        .append(new KeyedCodec<>("HomeBed", Vector3i.CODEC), (ai, val) -> ai.homeBed = val, ai -> ai.homeBed).add()
        .append(new KeyedCodec<>("State", Codec.STRING), (ai, val) -> ai.state = val, ai -> ai.state).add()
        .append(new KeyedCodec<>("VillageZoneUuid", Codec.UUID_BINARY), (ai, val) -> ai.villageZoneUuid = val, ai -> ai.villageZoneUuid).add()
        .append(new KeyedCodec<>("TargetPos", Vector3i.CODEC), (ai, val) -> ai.targetPos = val, ai -> ai.targetPos).add()
        .append(new KeyedCodec<>("PathQueue", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)),
            (ai, val) -> ai.pathQueue = (val == null ? new ArrayDeque<>() : new ArrayDeque<>(Arrays.asList(val))),
            ai -> ai.pathQueue == null ? new UUID[0] : ai.pathQueue.toArray(new UUID[0])).add()
        .build();

    private Vector3i homeBed;
    private UUID villageZoneUuid;
    private String state; // "NIGHT_SLEEP", "DAY_WANDER", "PATROLLING"
    private Vector3i targetPos;
    private ArrayDeque<UUID> pathQueue = new ArrayDeque<>();
    private int waitTicks = 0;

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    public void increaseWaitTicks() {
        this.waitTicks++;
    }

    // workDurationGoal as 24 hours ratio equivalent
    // Tell how much time a NPC aim to use for work related task
    public float workDurationGoal = 4.0f;
    // The current amount of in game seconds the NPC has work
    public float workDuration = 0.0f;

    // leisureDurationGoal as 24 hours ratio equivalent
    // Tell how much time a NPC aim to use for work related task
    public float leisureDurationGoal = 8.0f;
    public float leisureDuration = 0.0f;

    // Need between 0.0 and 100.0
    // 0.0 means really hungry
    // 100.0 mean well fed
    public float hunger = 100.0f;
    public float somnolence = 100.0f;
    public float social = 100.0f;

    public Map<UUID, Float> npcMeeted = new HashMap<>();
    public UUID lastGreetedNpc = null;
    public double lastGreetTime = 0.0;

    private HousedNpcPathSession pathSession = new HousedNpcPathSession();

    public HousedNpcEntity() {
        this.state = "PATHING_RANDOM";
    }

    public HousedNpcEntity(Vector3i homeBed, UUID villageZoneUuid) {
        this.homeBed = homeBed;
        this.villageZoneUuid = villageZoneUuid;
        this.state = "PATHING_RANDOM";
    }

    public Vector3i getHomeBed() {
        return homeBed;
    }

    public void setHomeBed(Vector3i homeBed) {
        this.homeBed = homeBed;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Vector3i getTargetPos() {
        return targetPos;
    }

    public void setTargetPos(Vector3i targetPos) {
        this.targetPos = targetPos;
    }

    public void setTargetPos(Vector3d targetPos) {
        this.targetPos = new Vector3i((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
    }
    
    public Queue<java.util.UUID> getPathQueue() {
        return pathQueue;
    }
    
    public void setPathQueue(ArrayDeque<UUID> pathQueue) {
        this.pathQueue = pathQueue;
    }

    public UUID getVillageZoneUuid() {
        return villageZoneUuid;
    }

    public HousedNpcPathSession getPathSession() {
        return pathSession;
    }

    public static ComponentType<EntityStore, HousedNpcEntity> getComponentType() {
        return Villagearia.instance().getAiHousedNpcComponentType();
    }
    
    @Override
    public HousedNpcEntity clone() {
        var cloned = new HousedNpcEntity(this.homeBed, villageZoneUuid);
        cloned.setState(this.state);
        cloned.setWaitTicks(this.waitTicks);
        cloned.setTargetPos(this.targetPos);
        cloned.setPathQueue(new ArrayDeque<>(this.pathQueue));
        return cloned;
    }
}
