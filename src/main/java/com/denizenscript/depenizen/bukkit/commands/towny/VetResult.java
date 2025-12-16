package com.denizenscript.depenizen.bukkit.commands.towny;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.depenizen.bukkit.objects.towny.WorldCoordTag;
import com.palmergames.bukkit.towny.object.WorldCoord;

import java.util.List;

public class VetResult {
    public boolean result;
    public String error;
    public List<WorldCoord> validWorldCoords;
    public double cost;
    public VetResult(boolean result, String error, List<WorldCoord> validWorldCoords, double cost){
        this.result = result;
        this.error = error;
        this.validWorldCoords = validWorldCoords;
        this.cost = cost;
    }
    public VetResult(boolean result) {
        this(result, null, List.of(), 0.0);
    }
    public VetResult(boolean result, String error, List<WorldCoord> validWorldCoords) {
        this(result, error, validWorldCoords, 0.0);
    }
    public VetResult(boolean result, String error) {
        this(result, error, List.of(), 0.0);
    }
    // Helper: build output map from VetResult
    public static java.util.function.Function<VetResult, MapTag> ToMap =
            (vet) -> {
                MapTag out = new MapTag();
                if(vet == null)
                    return out;
                out.putObject("result", new ElementTag(vet.result));
                out.putObject("error", new ElementTag(vet.error == null ? "" : vet.error));
                out.putObject("selection", new ListTag(vet.validWorldCoords, wc -> new WorldCoordTag(wc)));
                out.putObject("cost", new ElementTag(vet.cost));
                return out;
            };
}
