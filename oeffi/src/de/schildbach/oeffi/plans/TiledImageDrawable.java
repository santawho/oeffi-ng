/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.plans;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class TiledImageDrawable extends Drawable {
    private final Bitmap nwBitmap;
    private final Bitmap neBitmap;
    private final Bitmap swBitmap;
    private final Bitmap seBitmap;

    private final int wWidth;
    private final int width;
    private final int nHeight;
    private final int height;

    private static final Paint PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);

    public TiledImageDrawable(final Bitmap bitmap) {
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        wWidth = width / 2;
        nHeight = height / 2;

        nwBitmap = Bitmap.createBitmap(bitmap, 0, 0, wWidth, nHeight);
        neBitmap = Bitmap.createBitmap(bitmap, wWidth, 0, width - wWidth, nHeight);
        swBitmap = Bitmap.createBitmap(bitmap, 0, nHeight, wWidth, height - nHeight);
        seBitmap = Bitmap.createBitmap(bitmap, wWidth, nHeight, width - wWidth, height - nHeight);
    }

    public TiledImageDrawable(
            final Bitmap nwBitmap,
            final Bitmap neBitmap,
            final Bitmap swBitmap,
            final Bitmap seBitmap) {
        this.nwBitmap = nwBitmap;
        this.neBitmap = neBitmap;
        this.swBitmap = swBitmap;
        this.seBitmap = seBitmap;

        this.wWidth = Math.max(nwBitmap.getWidth(), swBitmap.getWidth());
        this.width = wWidth + Math.max(neBitmap.getWidth(), seBitmap.getWidth());
        this.nHeight = Math.max(nwBitmap.getHeight(), neBitmap.getHeight());
        this.height = nHeight + Math.max(swBitmap.getHeight(), seBitmap.getHeight());
    }

    @Override
    public void draw(final Canvas canvas) {
        canvas.drawBitmap(nwBitmap, 0.0f, 0.0f, PAINT);
        canvas.drawBitmap(neBitmap, wWidth, 0.0f, PAINT);
        canvas.drawBitmap(swBitmap, 0.0f, nHeight, PAINT);
        canvas.drawBitmap(seBitmap, wWidth, nHeight, PAINT);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public int getMinimumWidth() {
        return width;
    }

    @Override
    public int getMinimumHeight() {
        return height;
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
        // not implemented
    }

    @Override
    public void setAlpha(final int alpha) {
        // not implemented
    }
}
