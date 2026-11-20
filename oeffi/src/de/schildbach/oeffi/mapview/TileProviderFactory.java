package de.schildbach.oeffi.mapview;

import android.content.Context;

import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;

import de.schildbach.oeffi.Application;

public class TileProviderFactory {
    public static final OnlineTileSourceBase FACIL_PUBLIC_TRANSPORT_OVERLAY = new XYTileSource(
            "FacilMapPublicTransport", 0, 19, 256, ".png",
            new String[]{"https://pt.facilmap.org/tile/"}, "© FacilMap & OpenPTMap");
    // alternative: see https://www.xn--pnvkarte-m4a.de/

    public static MapTileProviderBase getBaseTileProvider(final Context context) {
        final String customUrl = Application.getInstance().getPreferences().getString("map_custom_provider_url");
        return getBaseTileProvider(context, customUrl);
    }

    public static MapTileProviderBase getBaseTileProvider(final Context context, final String urlPattern) {
        final ITileSource tileSource;
        if (urlPattern != null && !urlPattern.isEmpty()) {
            tileSource = new UrlPatternTileSource(urlPattern);
        } else {
            tileSource = getBaseTileSource();
        }
        return new MapTileProviderBasic(context.getApplicationContext(), tileSource);
    }

    public static ITileSource getBaseTileSource() {
        return TileSourceFactory.MAPNIK;
    }

    public static class UrlPatternTileSource extends OnlineTileSourceBase {
        // https://example.com/{z}/{y}/{x}.png?token=ABC
        // https://c.tile.thunderforest.com/cycle/{z}/{x}/{y}.png?apikey=bc74ceb5f91c448b9615f9b576c61c16
        public UrlPatternTileSource(final String urlPattern) {
            super(
                    "userdefined-" + urlPattern.hashCode(),
                    0, 19, 256,
                    ".png",
                    new String[]{ urlPattern },
                    "custom provider");
        }

        @Override
        public String getTileURLString(final long pMapTileIndex) {
            return getBaseUrl()
                    .replace("{z}", Integer.toString(MapTileIndex.getZoom(pMapTileIndex)))
                    .replace("{x}", Integer.toString(MapTileIndex.getX(pMapTileIndex)))
                    .replace("{y}", Integer.toString(MapTileIndex.getY(pMapTileIndex)));
        }

        @Override
        public String getTileRelativeFilenameString(final long pMapTileIndex) {
            return super.getTileRelativeFilenameString(pMapTileIndex);
        }
    }
}
