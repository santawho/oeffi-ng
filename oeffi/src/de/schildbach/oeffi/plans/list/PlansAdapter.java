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
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.URLs;
import de.schildbach.oeffi.plans.PlanContentProvider;
import de.schildbach.pte.NetworkId;
import okhttp3.Cache;
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
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlansAdapter extends RecyclerView.Adapter<PlanViewHolder> {
    private static final Logger log = LoggerFactory.getLogger(PlansAdapter.class);
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
    private final PlanClickListener clickListener;
    private final PlanContextMenuItemListener contextMenuItemListener;

    private final Handler handler = new Handler();
    private final OkHttpClient cachingOkHttpClient;

    public PlansAdapter(final Context context, final Cursor cursor, final Cache thumbCache,
            final PlanClickListener clickListener, final PlanContextMenuItemListener contextMenuItemListener,
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

        setHasStableIds(true);

        cachingOkHttpClient = okHttpClient.newBuilder().cache(thumbCache).build();
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
        holder.bind(getPlan(position), clickListener, contextMenuItemListener);
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

    @Override
    public void onViewAttachedToWindow(final PlanViewHolder holder) {
        final int position = holder.getAdapterPosition();
        final Plan plan = getPlan(position);
        if (holder.getCall() == null) {
            final HttpUrl thumbUrl = URLs.getPlansBaseUrl().newBuilder()
                    .addEncodedPathSegment(plan.planId + "_thumb.png").build();
            final Request request = new Request.Builder().url(thumbUrl).build();
            final Call call = cachingOkHttpClient.newCall(request);
            holder.setCall(call);
            call.enqueue(new Callback() {
                public void onResponse(final Call call, final Response r) throws IOException {
                    try (final Response response = r) {
                        final Drawable thumb;
                        if (response.isSuccessful())
                            thumb = new BitmapDrawable(res, response.body().byteStream());
                        else
                            thumb = res.getDrawable(R.drawable.ic_oeffi_plans_grey300_72dp).mutate();
                        if (!call.isCanceled()) {
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
            });
        }
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
        cursor.moveToPosition(position);
        final long rowId = cursor.getLong(rowIdColumn);
        final String planId = cursor.getString(planIdColumn);
        final String name = cursor.getString(nameColumn);
        final String disclaimer = cursor.getString(disclaimerColumn);
        final long validFromLong = cursor.getLong(validFromColumn);
        final Date validFrom = validFromLong != 0 ? new Date(validFromLong) : null;
        final String networkLogo = cursor.getString(networkLogoColumn);
        final String urlStr = cursor.getString(urlColumn);
        final HttpUrl url = urlStr != null ? HttpUrl.parse(urlStr) : null;
        final File localFile = new File(context.getDir(Constants.PLANS_DIR, Context.MODE_PRIVATE), planId + ".png");
        NetworkId networkId = null;
        if (networkLogo != null) {
            try {
                networkId = NetworkId.valueOf(networkLogo);
            } catch (IllegalArgumentException iae) {
                log.warn("network plan \"{}\" references undefined network id \"{}\"", planId, networkLogo);
            }
        }
        return new Plan(rowId, planId, name, disclaimer, validFrom, networkId, url, localFile);
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
        @Nullable
        public final HttpUrl url;
        public final File localFile;

        protected Plan(final long rowId, final String planId, final String name, final String disclaimer,
                final Date validFrom, final NetworkId networkId, final HttpUrl url, final File localFile) {
            this.rowId = rowId;
            this.planId = checkNotNull(planId);
            this.name = checkNotNull(name);
            this.disclaimer = disclaimer;
            this.validFrom = validFrom;
            this.networkId = networkId;
            this.url = url;
            this.localFile = checkNotNull(localFile);
        }
    }
}
