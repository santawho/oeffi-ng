package de.schildbach.oeffi.mapview;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.views.overlay.TilesOverlay;

public class TileSourceTilesOverlay extends TilesOverlay {
    private final ITileSource tileSource;

    public TileSourceTilesOverlay(final Context context, final ITileSource tileSource) {
        super(new MapTileProviderBasic(context.getApplicationContext(), tileSource), context.getApplicationContext());
        this.tileSource = tileSource;
        setLoadingBackgroundColor(Color.TRANSPARENT);
    }

    public ITileSource getTileSource() {
        return tileSource;
    }
}
