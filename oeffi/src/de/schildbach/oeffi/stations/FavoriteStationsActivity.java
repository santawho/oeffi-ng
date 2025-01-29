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

package de.schildbach.oeffi.stations;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ViewAnimator;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.stations.list.FavoriteStationsAdapter;
import de.schildbach.oeffi.stations.list.StationClickListener;
import de.schildbach.oeffi.stations.list.StationContextMenuItemListener;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Location;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class FavoriteStationsActivity extends OeffiActivity
        implements StationClickListener, StationContextMenuItemListener {
    private static final String INTENT_EXTRA_NETWORK = FavoriteStationsActivity.class.getName() + ".network";

    public static void start(final Context context, final boolean newTask) {
        final Intent intent = new Intent(context, FavoriteStationsActivity.class);
        if (newTask)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static class PickFavoriteStation extends ActivityResultContract<NetworkId, Uri> {
        @Override
        public Intent createIntent(final Context context, final NetworkId network) {
            final Intent intent = new Intent(context, FavoriteStationsActivity.class);
            intent.putExtra(INTENT_EXTRA_NETWORK, checkNotNull(network));
            return intent;
        }

        @Override
        public Uri parseResult(final int resultCode, @Nullable final Intent intent) {
            if (resultCode == Activity.RESULT_OK && intent != null)
                return intent.getData();
            else
                return null;
        }
    }

    @Nullable
    private NetworkId network;

    private ViewAnimator viewAnimator;
    private RecyclerView listView;
    private FavoriteStationsAdapter adapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);

        setContentView(R.layout.favorites_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar_stations);
        actionBar.setPrimaryTitle(getTitle());
        actionBar.setBack(isTaskRoot() ? null : v -> finish());

        viewAnimator = findViewById(R.id.favorites_layout);

        listView = findViewById(R.id.favorites_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        adapter = new FavoriteStationsAdapter(this, network, this, network == null ? this : null);
        listView.setAdapter(adapter);
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        updateGUI();
    }

    public void onStationClick(final int adapterPosition, final NetworkId stationNetwork, final Location station) {
        final boolean shouldReturnResult = network != null; // TODO hacky

        if (shouldReturnResult) {
            final Intent intent = new Intent();
            intent.setData(Uri.withAppendedPath(FavoriteStationsProvider.CONTENT_URI(),
                    String.valueOf(adapter.getItemId(adapterPosition))));
            setResult(RESULT_OK, intent);
            finish();
        } else {
            StationDetailsActivity.start(FavoriteStationsActivity.this, stationNetwork, station);
        }
    }

    public boolean onStationContextMenuItemClick(final int adapterPosition, final NetworkId stationNetwork,
            final Location station, final @Nullable List<Departure> departures, final int menuItemId) {
        if (menuItemId == R.id.station_context_details) {
            StationDetailsActivity.start(FavoriteStationsActivity.this, stationNetwork, station, departures);
            return true;
        } else if (menuItemId == R.id.station_context_remove_favorite) {
            adapter.removeEntry(adapterPosition);
            updateGUI();
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else {
            return false;
        }
    }

    public void updateGUI() {
        viewAnimator.setDisplayedChild(adapter.getItemCount() > 0 ? 0 : 1);
    }
}
