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

package de.schildbach.oeffi.network.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;

import java.util.LinkedList;
import java.util.List;

public class NetworksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final Context context;
    private final LayoutInflater inflater;
    private final NetworkId previouslySelectedNetwork;
    private final NetworkClickListener clickListener;
    private final NetworkContextMenuItemListener contextMenuItemListener;

    private final List<NetworkListEntry> entries = new LinkedList<>();

    public NetworksAdapter(final Context context, final NetworkId previouslySelectedNetwork,
            final NetworkClickListener clickListener, final NetworkContextMenuItemListener contextMenuItemListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.previouslySelectedNetwork = previouslySelectedNetwork;
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;

        setHasStableIds(true);
    }

    public void setEntries(final List<NetworkListEntry> entries) {
        this.entries.clear();
        this.entries.addAll(entries);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @Override
    public int getItemViewType(final int position) {
        if (entries.get(position) instanceof NetworkListEntry.Separator)
            return R.layout.network_picker_separator;
        else
            return R.layout.network_picker_entry;
    }

    @Override
    public long getItemId(final int position) {
        return entries.get(position).hashCode();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == R.layout.network_picker_separator)
            return new SeparatorViewHolder(inflater.inflate(R.layout.network_picker_separator, parent, false));
        else
            return new NetworkViewHolder(context, inflater.inflate(R.layout.network_picker_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof SeparatorViewHolder) {
            ((SeparatorViewHolder) holder).bind((NetworkListEntry.Separator) entries.get(position));
        } else {
            final NetworkListEntry.Network entry = (NetworkListEntry.Network) entries.get(position);
            if (!NetworkListEntry.Network.STATE_DISABLED.equals(entry.state)) {
                ((NetworkViewHolder) holder).bind(entry, true, 0, clickListener, null);
            } else {
                ((NetworkViewHolder) holder).bind(entry, false, 0, null, null);
            }
        }
    }
}
