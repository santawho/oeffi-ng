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

package de.schildbach.oeffi.plans.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.URLs;
import de.schildbach.oeffi.plans.PlanContentProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Point;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlansAdapter extends RecyclerView.Adapter<PlanViewHolder> {
    private static final Logger log = LoggerFactory.getLogger(PlansAdapter.class);

    private static final int LOCAL_CACHE_DURATION_SECONDS = 10 * 60;

    private final Context context;
    private final Resources res;
    private final LayoutInflater inflater;
    private final Cursor cursor;
    private final int rowIdColumn;
    private final int planIdColumn;
    private final int nameColumn;
    private final int disclaimerColumn;
    private final int validFromColumn;
    private final int networkLogoColumn;
    private final int urlColumn;
    private final int latColumn;
    private final int lonColumn;
    private final PlanClickListener clickListener;
    private final PlanContextMenuItemListener contextMenuItemListener;
    private final Map<Integer, Plan> plansByPosition = new HashMap<>();

    private final Handler handler = new Handler();
    private final OkHttpClient cachingOkHttpClient;

    private Point location;

    public PlansAdapter(
            final Context context,
            final Cursor cursor,
            final Cache thumbCache,
            final PlanClickListener clickListener,
            final PlanContextMenuItemListener contextMenuItemListener,
            final OkHttpClient okHttpClient) {
        this.context = context;
        this.res = context.getResources();
        this.inflater = LayoutInflater.from(context);
        this.cursor = cursor;
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;

        rowIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        planIdColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_ID);
        nameColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_NAME);
        disclaimerColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_DISCLAIMER);
        validFromColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_VALID_FROM);
        networkLogoColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_NETWORK_LOGO);
        urlColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_REMOTE_URL);
        latColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_LAT);
        lonColumn = cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_LON);

        setHasStableIds(true);

        cachingOkHttpClient = okHttpClient.newBuilder()
                .cache(thumbCache)
                .addNetworkInterceptor(chain -> {
                    final Response response = chain.proceed(chain.request());
                    final int code = response.code();
                    if (code == 200
                            || code == 404) { // also cache 404 NOT FOUND
                        // enforce cache duration
                        return response.newBuilder()
                                .removeHeader("Pragma")
                                .removeHeader("Cache-Control")
                                .header("Cache-Control", "public, max-age=" + LOCAL_CACHE_DURATION_SECONDS)
                                .build();
                    }
                    return response;
                })
                .build();
    }

    public void setLocation(final Point location) {
        this.location = location;
        notifyDataSetChanged();
    }

    public void toggleFavorite(final PlansAdapter.Plan plan) {
        plan.isFavorite = PlanContentProvider.toggleFavorite(plan.getId());
        notifyDataSetChanged();
    }

    public void setProgressPermille(final int position, final int progressPermille) {
        notifyItemChanged(position, progressPermille);
    }

    public void setLoaded(final int position, final boolean loaded) {
        notifyItemChanged(position, loaded);
    }

    @Override
    public int getItemCount() {
        return cursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        cursor.moveToPosition(position);
        return cursor.getLong(rowIdColumn);
    }

    @Override
    public PlanViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new PlanViewHolder(context, inflater.inflate(R.layout.plans_picker_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(final PlanViewHolder holder, final int position) {
        holder.bind(getPlan(position), clickListener, contextMenuItemListener, location);
    }

    @Override
    public void onBindViewHolder(final PlanViewHolder holder, final int position, final List<Object> payloads) {
        if (payloads.isEmpty()) {
            // Full bind
            onBindViewHolder(holder, position);
        } else {
            // Partial bind
            for (final Object payload : payloads) {
                if (payload instanceof Drawable)
                    holder.bindThumb((Drawable) payload);
                else if (payload instanceof Integer)
                    holder.bindProgressPermille((int) payload);
                else if (payload instanceof Boolean)
                    holder.bindLoaded((boolean) payload);
            }
        }
    }

    public void onViewAttachedToWindow(final PlanViewHolder holder) {
        final int position = holder.getAdapterPosition();
        final Plan plan = getPlan(position);
        if (plan.thumb != null)
            return;
        if (holder.getCall() != null)
            return;
        final HttpUrl thumbUrl = URLs.getPlansBaseUrl().newBuilder()
                .addEncodedPathSegment(plan.planId + "_thumb.png").build();
        // first attempt, take directly from the cache
        final Call call = cachingOkHttpClient.newCall(new Request.Builder()
                .url(thumbUrl)
                .cacheControl(new CacheControl.Builder()
                        .onlyIfCached()
                        .maxAge(LOCAL_CACHE_DURATION_SECONDS, TimeUnit.SECONDS)
                        .build())
                .build());
        holder.setCall(call);
        call.enqueue(downloadThumbCall(true, plan, holder));
    }

    private Callback downloadThumbCall(
            final boolean accept504notInCache,
            final Plan plan,
            final PlanViewHolder holder) {
        return new Callback() {
            public void onResponse(@NonNull final Call finishedCall, @NonNull final Response aResponse) throws IOException {
                try (final Response response = aResponse) {
                    if (!finishedCall.isCanceled()) {
                        if (accept504notInCache && response.code() == 504) {
                            // not in the cache
                            // second attempt, contact the server, might still return 304 NOT MODIFIED
                            final Call realCall = cachingOkHttpClient.newCall(new Request.Builder()
                                    .url(finishedCall.request().url())
                                    .build());
                            holder.setCall(realCall);
                            realCall.enqueue(downloadThumbCall(false, plan, holder));
                            return;
                        }
                        final Drawable thumb;
                        if (response.isSuccessful())
                            thumb = new BitmapDrawable(res, response.body().byteStream());
                        else
                            thumb = ResourcesCompat.getDrawable(res, R.drawable.ic_oeffi_plans_grey300_72dp, null).mutate();
                        plan.thumb = thumb;
                        handler.post(() -> {
                            holder.setCall(null);
                            final int position1 = holder.getAdapterPosition();
                            if (position1 != RecyclerView.NO_POSITION)
                                notifyItemChanged(position1, thumb);
                        });
                    }
                }
            }

            public void onFailure(final Call call, final IOException e) {
                handler.post(() -> holder.setCall(null));
            }
        };
    }

    @Override
    public void onViewDetachedFromWindow(final PlanViewHolder holder) {
        final Call call = holder.getCall();
        if (call != null && !call.isCanceled()) {
            call.cancel();
            holder.setCall(null);
        }
    }

    public Plan getPlan(final int position) {
        final Plan plan = plansByPosition.get(position);
        if (plan != null)
            return plan;
        cursor.moveToPosition(position);
        final long rowId = cursor.getLong(rowIdColumn);
        final String planId = cursor.getString(planIdColumn);
        final String name = cursor.getString(nameColumn);
        final String disclaimer = cursor.getString(disclaimerColumn);
        final long validFromLong = cursor.getLong(validFromColumn);
        final Date validFrom = validFromLong != 0 ? new Date(validFromLong) : null;
        final String networkLogo = cursor.getString(networkLogoColumn);
        final String urlStr = cursor.getString(urlColumn);
        final Point centerLocation = Point.from1E6(cursor.getInt(latColumn), cursor.getInt(lonColumn));
        NetworkId networkId = null;
        if (networkLogo != null) {
            try {
                networkId = NetworkId.valueOf(networkLogo);
            } catch (final IllegalArgumentException iae) {
                log.warn("network plan \"{}\" references undefined network id \"{}\"", planId, networkLogo);
            }
        }
        final Plan newPlan = new Plan(
                rowId, planId, name, disclaimer,
                validFrom, networkId, urlStr,
                centerLocation,
                PlanContentProvider.isPlanFavorite(planId));
        plansByPosition.put(position, newPlan);
        return newPlan;
    }

    public static class Plan {
        public final long rowId;
        public final String planId;
        public final String name;
        @Nullable
        public final String disclaimer;
        @Nullable
        public final Date validFrom;
        @Nullable
        public final NetworkId networkId;
        public Boolean isPDF;
        @Nullable
        public final HttpUrl url;
        public final Point centerPosition;
        public boolean isFavorite;
        public Drawable thumb;

        private Plan(
                final long rowId, final String planId, final String name, final String disclaimer,
                final Date validFrom, final NetworkId networkId, final String url,
                final Point centerPosition, final boolean isFavorite) {
            this.rowId = rowId;
            this.planId = requireNonNull(planId);
            this.name = requireNonNull(name);
            this.disclaimer = disclaimer;
            this.validFrom = validFrom;
            this.networkId = networkId;
            this.isPDF = PlanContentProvider.isPdfUrl(url);
            this.url = PlanContentProvider.getValidUrl(url);
            this.centerPosition = centerPosition;
            this.isFavorite = isFavorite;
        }

        public String getId() {
            return planId;
        }

        public File getPlanFile() {
            return PlanContentProvider.instance.getPlanFile(planId, isPDF);
        }

        public String getPlanFilename() {
            return PlanContentProvider.getPlanFilename(planId, isPDF);
        }
    }
}
