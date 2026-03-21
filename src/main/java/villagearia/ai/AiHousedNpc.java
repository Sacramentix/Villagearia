package villagearia.ai;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;
import villagearia.component.VillageZone;

public class AiHousedNpc implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<AiHousedNpc> CODEC = BuilderCodec.builder(
            AiHousedNpc.class, AiHousedNpc::new
        )
        .append(new KeyedCodec<>("HomeBed", Vector3i.CODEC), (ai, val) -> ai.homeBed = val, ai -> ai.homeBed).add()
        .append(new KeyedCodec<>("State", Codec.STRING), (ai, val) -> ai.state = val, ai -> ai.state).add()
        .append(new KeyedCodec<>("TargetPos", Vector3i.CODEC), (ai, val) -> ai.targetPos = val, ai -> ai.targetPos).add()
        .build();

    private Vector3i homeBed;
    private String state; // "NIGHT_SLEEP", "DAY_WANDER"
    private int waitTicks;
    private Vector3i targetPos;

    public AiHousedNpc() {
        this.state = "DAY_WANDER";
        this.waitTicks = 0;
    }

    public AiHousedNpc(Vector3i homeBed) {
        this.homeBed = homeBed;
        this.state = "DAY_WANDER";
        this.waitTicks = 0;
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

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int ticks) {
        this.waitTicks = ticks;
    }

    public Vector3i getTargetPos() {
        return targetPos;
    }

    public void setTargetPos(Vector3i targetPos) {
        this.targetPos = targetPos;
    }
    
    public void decreaseWaitTicks() {
        if(this.waitTicks > 0) this.waitTicks--;
    }

    public static ComponentType<EntityStore, AiHousedNpc> getComponentType() {
        return Villagearia.get().getAiHousedNpcComponentType();
    }
    
    @Override
    public AiHousedNpc clone() {
        var cloned = new AiHousedNpc(this.homeBed);
        cloned.setState(this.state);
        cloned.setWaitTicks(this.waitTicks);
        cloned.setTargetPos(this.targetPos);
        return cloned;
    }
}
