package villagearia.utils;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.assetstore.AssetRegistry;
import java.util.Set;
import java.util.HashSet;

public class ItemTagHelper {

    private static final int INGREDIENT_TYPE_TAG = AssetRegistry.getOrCreateTagIndex("Type=Ingredient");

    private static final int LEATHER_FAMILY_TAG = AssetRegistry.getOrCreateTagIndex("Family=Leather");

    public static Set<String> getLeatherIngredients() {
        var leatherItems = Item.getAssetMap().getKeysForTag(LEATHER_FAMILY_TAG);
        var ingredientItems = Item.getAssetMap().getKeysForTag(INGREDIENT_TYPE_TAG);
        var leatherIngredients = new HashSet<>(leatherItems);
        leatherIngredients.retainAll(ingredientItems);
        
        return leatherIngredients;
    }

    private static final int HIDE_FAMILY_TAG = AssetRegistry.getOrCreateTagIndex("Family=Hide");

    public static Set<String> getHideIngredients() {
        var hideItems = Item.getAssetMap().getKeysForTag(HIDE_FAMILY_TAG);
        var ingredientItems = Item.getAssetMap().getKeysForTag(INGREDIENT_TYPE_TAG);
        var hideIngredients = new HashSet<>(hideItems);
        hideIngredients.retainAll(ingredientItems);
        
        return hideIngredients;
    }
}