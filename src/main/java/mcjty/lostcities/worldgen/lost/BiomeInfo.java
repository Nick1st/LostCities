package mcjty.lostcities.worldgen.lost;

import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.varia.ChunkCoord;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;

public class BiomeInfo {

    private static Map<ChunkCoord, BiomeInfo> biomeInfoMap = new HashMap<>();

    private Biome[] biomesForBiomeCheck = null;
    private Biome mainBiome;

    public static void cleanCache() {
        biomeInfoMap.clear();
    }

    public static BiomeInfo getBiomeInfo(IDimensionInfo provider, ChunkCoord coord) {
        if (!biomeInfoMap.containsKey(coord)) {
            BiomeInfo info = new BiomeInfo();
            int chunkX = coord.getChunkX();
            int chunkZ = coord.getChunkZ();
            info.biomesForBiomeCheck = provider.getBiomes(chunkX, chunkZ);
            info.mainBiome = provider.getBiome(new BlockPos((chunkX << 4) + 8, 65, (chunkZ << 4) + 8));
            biomeInfoMap.put(coord, info);
        }
        return biomeInfoMap.get(coord);
    }

    public Biome[] getBiomes() {
        return biomesForBiomeCheck;
    }

    public Biome getMainBiome() {
        return mainBiome;
    }
}
