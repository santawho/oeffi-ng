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
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkResources;
import okhttp3.Call;

import javax.annotation.Nullable;
import java.util.Date;

public class PlanViewHolder extends RecyclerView.ViewHolder {
    private final Context context;
    private final java.text.DateFormat dateFormat;
    private final ImageView thumbView;
    private final TextView nameView;
    private final TextView disclaimerView;
    private final ImageView loadedView;
    private final ProgressBar progressView;
    private final TextView validFromView;
    private final ImageView networkLogoView;
    private final ImageButton contextButton;

    @Nullable
    private Call call = null;

    public PlanViewHolder(final Context context, final View itemView) {
        super(itemView);
        this.context = context;
        this.dateFormat = DateFormat.getDateFormat(context);

        thumbView = itemView.findViewById(R.id.plans_picker_entry_thumb);
        nameView = itemView.findViewById(R.id.plans_picker_entry_name);
        disclaimerView = itemView.findViewById(R.id.plans_picker_entry_disclaimer);
        loadedView = itemView.findViewById(R.id.plans_picker_entry_loaded);
        progressView = itemView.findViewById(R.id.plans_picker_entry_progress);
        validFromView = itemView.findViewById(R.id.plans_picker_entry_valid_from);
        networkLogoView = itemView.findViewById(R.id.plans_picker_entry_network_logo);
        contextButton = itemView.findViewById(R.id.plans_picker_entry_context_button);
    }

    public void bind(final PlansAdapter.Plan plan, final PlanClickListener clickListener,
            final PlanContextMenuItemListener contextMenuItemListener) {
        itemView.setOnClickListener(v -> clickListener.onPlanClick(plan));

        thumbView.setImageDrawable(null);

        nameView.setText(plan.name);

        disclaimerView.setText(plan.disclaimer);

        loadedView.setVisibility(plan.localFile.exists() ? View.VISIBLE : View.GONE);

        progressView.setVisibility(View.INVISIBLE);

        final Date now = new Date();
        final Date validFrom = plan.validFrom;
        final boolean valid = validFrom == null || validFrom.before(now);
        validFromView.setText(
                valid ? null : context.getString(R.string.plans_picker_entry_valid_from, dateFormat.format(validFrom)));

        if (plan.networkId != null) {
            final NetworkResources networkResources = NetworkResources.instance(context, plan.networkId);
            networkLogoView.setVisibility(View.VISIBLE);
            networkLogoView.setImageDrawable(networkResources.icon);
        } else {
            networkLogoView.setVisibility(View.GONE);
        }

        contextButton.setOnClickListener(v -> {
            final PopupMenu contextMenu = new PopupMenu(context, v);
            contextMenu.inflate(R.menu.plans_picker_context);
            contextMenu.getMenu().findItem(R.id.plans_picker_context_remove).setVisible(plan.localFile.exists());
            contextMenu.setOnMenuItemClickListener(item -> contextMenuItemListener.onPlanContextMenuItemClick(plan,
                    item.getItemId()));
            contextMenu.show();
        });
    }

    public void bindThumb(final Drawable thumb) {
        if (thumbView.getDrawable() == null) {
            final Animation animation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
            animation.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(final Animation animation) {
                    setIsRecyclable(false);
                }

                public void onAnimationEnd(final Animation animation) {
                    setIsRecyclable(true);
                }

                public void onAnimationRepeat(final Animation animation) {
                    // Ignore
                }
            });
            thumbView.startAnimation(animation);
        }
        thumbView.setImageDrawable(thumb);
    }

    public void bindProgressPermille(final int progressPermille) {
        progressView.setVisibility(progressPermille > 0 ? View.VISIBLE : View.INVISIBLE);
        progressView.setProgress(progressPermille);
        if (progressPermille == 1000) {
            final Animation animation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
            animation.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(final Animation animation) {
                    setIsRecyclable(false);
                }

                public void onAnimationEnd(final Animation animation) {
                    progressView.setVisibility(View.INVISIBLE);
                    setIsRecyclable(true);
                }

                public void onAnimationRepeat(final Animation animation) {
                    // Ignore
                }
            });
            progressView.startAnimation(animation);
        }
    }

    public void bindLoaded(final boolean loaded) {
        if (loaded && loadedView.getVisibility() != View.VISIBLE) {
            loadedView.setVisibility(View.VISIBLE);
            final Animation animation = AnimationUtils.loadAnimation(context, R.anim.pop_in);
            animation.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(final Animation animation) {
                    setIsRecyclable(false);
                }

                public void onAnimationEnd(final Animation animation) {
                    setIsRecyclable(true);
                }

                public void onAnimationRepeat(final Animation animation) {
                    // Ignore
                }
            });
            loadedView.startAnimation(animation);
        } else if (!loaded && loadedView.getVisibility() == View.VISIBLE) {
            final Animation animation = AnimationUtils.loadAnimation(context, R.anim.pop_out);
            animation.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(final Animation animation) {
                    setIsRecyclable(false);
                }

                public void onAnimationEnd(final Animation animation) {
                    loadedView.setVisibility(View.GONE);
                    setIsRecyclable(true);
                }

                public void onAnimationRepeat(final Animation animation) {
                    // Ignore
                }
            });
            loadedView.startAnimation(animation);
        }
    }

    public void setCall(final Call call) {
        this.call = call;
    }

    @Nullable
    public Call getCall() {
        return call;
    }
}
