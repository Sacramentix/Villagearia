package villagearia.component.HousedNpcBlockOfInterest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
// Note: You will need to import Villagearia and register this in your main class!

public class HousedNpcBlockOfInterestComponent implements Component<EntityStore> {

    private HousedNpcBlockOfInterest type;
    private Set<UUID> villageAffiliations;

    public HousedNpcBlockOfInterestComponent() {
        this.villageAffiliations = new HashSet<>();
    }

    public HousedNpcBlockOfInterestComponent(HousedNpcBlockOfInterest type, Set<UUID> affiliations) {
        this.type = type;
        this.villageAffiliations = affiliations != null ? affiliations : new HashSet<>();
    }

    public HousedNpcBlockOfInterest getType() {
        return type;
    }

    public void setType(HousedNpcBlockOfInterest type) {
        this.type = type;
    }

    public Set<UUID> getVillageAffiliations() {
        return villageAffiliations;
    }

    public void setVillageAffiliations(Set<UUID> villageAffiliations) {
        this.villageAffiliations = villageAffiliations;
    }
    
    @Override
    public HousedNpcBlockOfInterestComponent clone() {
        return new HousedNpcBlockOfInterestComponent(this.type, new HashSet<>(this.villageAffiliations));
    }
    
    // public static ComponentType<EntityStore, HousedNpcBlockOfInterestComponent> getComponentType() {
    //     return Villagearia.instance().getHousedNpcBlockOfInterestComponentType();
    // }
}
