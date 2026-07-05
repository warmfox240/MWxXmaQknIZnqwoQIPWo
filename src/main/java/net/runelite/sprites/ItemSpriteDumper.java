package net.runelite.sprites;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.ItemSpriteFactory;

public class ItemSpriteDumper
{
	private static final String OPENRS2_CACHES_URL = "https://archive.openrs2.org/caches.json";

	public static void main(String[] args) throws Exception
	{
		File outDir = new File("items");
		File cacheDir;

		if (args.length >= 1)
		{
			cacheDir = new File(args[0]);
		}
		else
		{
			cacheDir = downloadLatestCache();
		}

		if (args.length >= 2)
		{
			outDir = new File(args[1]);
		}

		outDir.mkdirs();

		System.out.println("Using cache: " + cacheDir);
		System.out.println("Output directory: " + outDir);

		dumpItemSprites(cacheDir, outDir);
	}

	private static File downloadLatestCache() throws Exception
	{
		System.out.println("Fetching latest OSRS cache info from OpenRS2...");

		int cacheId = findLatestCacheId();
		System.out.println("Latest OSRS cache ID: " + cacheId);

		File cacheDir = new File("cache");
		cacheDir.mkdirs();

		String diskUrl = "https://archive.openrs2.org/caches/runescape/" + cacheId + "/disk.zip";
		System.out.println("Downloading cache from " + diskUrl);

		try (InputStream is = new URL(diskUrl).openStream();
			ZipInputStream zis = new ZipInputStream(is))
		{
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null)
			{
				if (entry.isDirectory())
				{
					continue;
				}

				String name = new File(entry.getName()).getName();
				File outFile = new File(cacheDir, name);
				try (FileOutputStream fos = new FileOutputStream(outFile))
				{
					byte[] buf = new byte[8192];
					int len;
					while ((len = zis.read(buf)) > 0)
					{
						fos.write(buf, 0, len);
					}
				}
			}
		}

		System.out.println("Cache downloaded to " + cacheDir);
		return cacheDir;
	}

	private static int findLatestCacheId() throws Exception
	{
		try (InputStream is = new URL(OPENRS2_CACHES_URL).openStream();
			InputStreamReader reader = new InputStreamReader(is))
		{
			JsonArray caches = new JsonParser().parse(reader).getAsJsonArray();

			int latestId = -1;
			String latestTimestamp = "";

			for (JsonElement el : caches)
			{
				JsonObject cache = el.getAsJsonObject();

				if (!"oldschool".equals(cache.get("game").getAsString()))
				{
					continue;
				}
				if (!"live".equals(cache.get("environment").getAsString()))
				{
					continue;
				}

				String timestamp = cache.get("timestamp").isJsonNull() ? "" : cache.get("timestamp").getAsString();
				if (timestamp.compareTo(latestTimestamp) > 0)
				{
					latestTimestamp = timestamp;
					latestId = cache.get("id").getAsInt();
				}
			}

			if (latestId == -1)
			{
				throw new RuntimeException("Could not find OSRS cache on OpenRS2");
			}

			return latestId;
		}
	}

	private static void dumpItemSprites(File cacheDir, File outDir) throws IOException
	{
		int count = 0;
		int errors = 0;

		try (Store store = new Store(cacheDir))
		{
			store.load();

			ItemManager itemManager = new ItemManager(store);
			itemManager.load();
			itemManager.link();

			ModelProvider modelProvider = (int modelId) ->
			{
				Index models = store.getIndex(IndexType.MODELS);
				Archive archive = models.getArchive(modelId);
				byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
				return new ModelLoader().load(modelId, data);
			};

			SpriteManager spriteManager = new SpriteManager(store);
			spriteManager.load();

			TextureManager textureManager = new TextureManager(store);
			textureManager.load();

			for (ItemDefinition itemDef : itemManager.getItems())
			{
				if (itemDef.name == null || itemDef.name.equalsIgnoreCase("null"))
				{
					continue;
				}

				try
				{
					BufferedImage sprite = ItemSpriteFactory.createSprite(
						itemManager, modelProvider, spriteManager, textureManager,
						itemDef.id, 1, 1, 3153952, false);

					if (sprite != null)
					{
						ImageIO.write(sprite, "PNG", new File(outDir, itemDef.id + ".png"));
						++count;
					}
				}
				catch (Exception ex)
				{
					++errors;
					System.err.println("Error dumping item " + itemDef.id + ": " + ex.getMessage());
				}
			}
		}

		System.out.println("Dumped " + count + " item sprites (" + errors + " errors)");
	}
}
