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

package de.schildbach.oeffi.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.VehicleInformation;

public class VehicleInformationRenderer {
    final VehicleInformation vehicleInformation;
    final boolean showBicycle;
    final boolean showWheelchair;

    public VehicleInformationRenderer(
            final VehicleInformation vehicleInformation,
            final boolean showBicycle,
            final boolean showWheelchair) {
        this.vehicleInformation = vehicleInformation;
        this.showBicycle = showBicycle;
        this.showWheelchair = showWheelchair;
    }

    private static class Entry {
        double fromMeters;
        boolean platformEnd;
        boolean vehicleEnd;
        VehicleInformation.PlatformSection platformSection;
        VehicleInformation.VehicleData vehicleData;
    }

    private Context context;
    private LayoutInflater layoutInflater;
    private GridLayout gridLayout;
    private boolean showAllInformation;
    private int rowNumber;

    public void showVehicleInformationDialog(final Context context, final Runnable onDismissHandler) {
        this.context = context;

        final DialogBuilder dialogBuilder = DialogBuilder.get(context, R.layout.vehicle_information);
        final View contentView = dialogBuilder.getView();

        layoutInflater = LayoutInflater.from(context);

        final TextView positionView = contentView.findViewById(R.id.vehicle_information_position);
        if (vehicleInformation.platform == null || vehicleInformation.platform.name == null) {
            positionView.setVisibility(View.GONE);
        } else {
            final String platformName = vehicleInformation.platform.name;
            final SpannableStringBuilder positionStr = new SpannableStringBuilder(
                    Formats.makeBreakablePositionName(platformName)
                            .replace('\u200B', '\n'));
            positionView.setText(positionStr);
        }

        gridLayout = contentView.findViewById(R.id.vehicle_information_grid);

        final CheckBox showall = contentView.findViewById(R.id.vehicle_information_showall);
        showall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showAllInformation = isChecked;
            render();
        });

        render();

        final AlertDialog dialog = dialogBuilder
                .setCanceledOnTouchOutside(true)
                .setOnDismissListener(d -> onDismissHandler.run())
                .show();
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (context.getResources().getDisplayMetrics().heightPixels * 0.75));
        }
    }

    private void render() {
        final boolean reverseOrientation = vehicleInformation.reverseOrientationAtPlatform;

        final VehicleInformation.PlatformSection platform = vehicleInformation.platform;
        final List<VehicleInformation.PlatformSection> platformSections = vehicleInformation.platformSections;
        final int numPlatformSections = platformSections == null ? 0 : platformSections.size();

        Entry entry;
        final List<Entry> list = new ArrayList<>();
        if (numPlatformSections == 0) {
            entry = new Entry();
            entry.fromMeters = reverseOrientation ? -platform.toMeters : platform.fromMeters;
            list.add(entry);

            entry = new Entry();
            entry.platformEnd = true;
            entry.fromMeters = reverseOrientation ? -platform.fromMeters : platform.toMeters;
            list.add(entry);
        } else if (reverseOrientation) {
            final double sectionToMeters = platformSections.get(numPlatformSections - 1).toMeters;
            final double platformToMeters = platform.toMeters;
            if (platformToMeters > sectionToMeters) {
                entry = new Entry();
                entry.fromMeters = -platformToMeters;
                list.add(entry);
            }

            for (int index = numPlatformSections - 1; index >= 0; --index) {
                final VehicleInformation.PlatformSection section = platformSections.get(index);

                entry = new Entry();
                entry.platformSection = section;
                entry.fromMeters = -section.toMeters;
                list.add(entry);
            }

            final double sectionFromMeters = platformSections.get(0).fromMeters;
            final double platformFromMeters = platform.fromMeters;
            if (sectionFromMeters > platformFromMeters) {
                entry = new Entry();
                entry.fromMeters = -sectionFromMeters;
                list.add(entry);
            }

            entry = new Entry();
            entry.platformEnd = true;
            entry.fromMeters = -platformFromMeters;
            list.add(entry);
        } else {
            final double sectionFromMeters = platformSections.get(0).fromMeters;
            final double platformFromMeters = platform.fromMeters;
            if (sectionFromMeters > platformFromMeters) {
                entry = new Entry();
                entry.fromMeters = platform.fromMeters;
                list.add(entry);
            }

            for (int index = 0; index < numPlatformSections; ++index) {
                final VehicleInformation.PlatformSection section = platformSections.get(index);

                entry = new Entry();
                entry.platformSection = section;
                entry.fromMeters = section.fromMeters;
                list.add(entry);
            }

            final double sectionToMeters = platformSections.get(numPlatformSections - 1).toMeters;
            final double platformToMeters = platform.toMeters;
            if (sectionToMeters < platformToMeters) {
                entry = new Entry();
                entry.fromMeters = sectionToMeters;
                list.add(entry);
            }

            entry = new Entry();
            entry.platformEnd = true;
            entry.fromMeters = platformToMeters;
            list.add(entry);
        }

        VehicleInformation.VehicleData lastVehicleData = null;
        final double reverseFactor = reverseOrientation ? -1 : 1;
        for (final VehicleInformation.VehicleGroup vehicleGroup : vehicleInformation.vehicleGroups) {
            for (final VehicleInformation.VehicleData vehicleData : vehicleGroup.vehicles) {
                lastVehicleData = vehicleData;
                entry = new Entry();
                entry.vehicleData = vehicleData;
                final VehicleInformation.PlatformSegment segment = vehicleData.platformSegment;
                entry.fromMeters = segment.fromMeters * reverseFactor;
                list.add(entry);
            }
        }
        entry = new Entry();
        entry.vehicleEnd = true;
        entry.fromMeters = lastVehicleData.platformSegment.toMeters * reverseFactor;
        list.add(entry);

        list.sort((e1, e2) -> {
            final double d = e1.fromMeters - e2.fromMeters;
            return d < 0 ? -1 : d > 0 ? 1 : 0;
        });

        int nextPlatformSectionIndex = findNext(list, -1, false);
        int nextVehicleIndex = findNext(list, -1, true);

        gridLayout.removeAllViews();
        entry = list.get(0);
        rowNumber = 0;
        if (nextPlatformSectionIndex == 0) {
            nextPlatformSectionIndex = findNext(list, 0, false);
            addTableDataPlatform(entry.platformSection, nextPlatformSectionIndex);
            addTableDataMeters(entry.fromMeters);
            addTableDataVehicle(null, nextVehicleIndex);
        } else {
            nextVehicleIndex = findNext(list, 0, true);
            addTableDataPlatform(null, nextPlatformSectionIndex);
            addTableDataMeters(entry.fromMeters);
            addTableDataVehicle(entry.vehicleData, nextVehicleIndex);
        }

        for (int i = 1, listSize = list.size(); i < listSize; i++) {
            entry = list.get(i);
            rowNumber += 1;
            if (i == nextPlatformSectionIndex) {
                nextPlatformSectionIndex = findNext(list, i, false);
                addTableDataPlatform(entry.platformSection, nextPlatformSectionIndex - i);
                addTableDataMeters(entry.fromMeters);
            } else {
                nextVehicleIndex = findNext(list, i, true);
                addTableDataMeters(entry.fromMeters);
                addTableDataVehicle(entry.vehicleData, nextVehicleIndex - i);
            }
        }

        gridLayout.setColumnCount(3);
        gridLayout.setRowCount(rowNumber + 1);
    }

    private static int findNext(final List<Entry> list, final int consumedStartIndex, final boolean vehicle) {
        for (int i = consumedStartIndex + 1, listSize = list.size(); i < listSize; i++) {
            final Entry e = list.get(i);
            final boolean isVehicle = e.vehicleData != null || e.vehicleEnd;
            if (vehicle == isVehicle)
                return i;
        }
        return list.size();
    }

    private <ViewType extends View> ViewType addCell(
            final ViewType cellView,
            final int columnNumber, final int rowSpan) {
        final GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(
                GridLayout.spec(rowNumber, rowSpan, GridLayout.FILL),
                GridLayout.spec(columnNumber, 1, GridLayout.FILL));
        cellView.setLayoutParams(layoutParams);
        gridLayout.addView(cellView);
        return cellView;
    }

    private <ViewType extends View> ViewType addCell(
            final Class<ViewType> viewTypeClass, final int layoutId,
            final int columnNumber, final int rowSpan) {
        return viewTypeClass.cast(addCell(layoutInflater.inflate(layoutId, null), columnNumber, rowSpan));
    }

    private void addGap(final int columnNumber, final int rowSpan) {
        addCell(new TextView(context), columnNumber, rowSpan);
    }

    @SuppressLint("DefaultLocale")
    private void addTableDataMeters(final double meters) {
        final ViewGroup metersLayout = addCell(ViewGroup.class, R.layout.vehicle_information_meters, 1, 1);

        final TextView textView = metersLayout.findViewById(R.id.vehicle_information_meters_text);
        textView.setText(String.format("%.0f", Math.abs(meters)));
    }

    private void addTableDataPlatform(final VehicleInformation.PlatformSection platformSection, final int span) {
        if (platformSection == null) {
            addGap(0, span);
            return;
        }

        final ViewGroup platformLayout = addCell(ViewGroup.class, R.layout.vehicle_information_platform, 0, span);

        final TextView sectionLabel = platformLayout.findViewById(R.id.vehicle_information_section_label);
        sectionLabel.setText(platformSection.name);
    }

    @SuppressLint("DefaultLocale")
    private void addTableDataVehicle(final VehicleInformation.VehicleData vehicleData, final int span) {
        if (vehicleData == null) {
            addGap(2, span);
            return;
        }

        final ViewGroup vehicleLayout = addCell(ViewGroup.class, R.layout.vehicle_information_vehicle, 2, span);
        final GridLayout.LayoutParams layoutParams = (GridLayout.LayoutParams) vehicleLayout.getLayoutParams();
        if (vehicleData.indexInGroup == 0) {
            layoutParams.topMargin = context.getResources().getDimensionPixelSize(R.dimen.text_padding_vertical_lax);
        }
        if (vehicleData.indexInGroup == vehicleData.group.vehicles.size() - 1) {
            layoutParams.bottomMargin = context.getResources().getDimensionPixelSize(R.dimen.text_padding_vertical_lax);
        }

        final int backgroundDrawableId;
        if (vehicleData.firstClass) {
            if (vehicleData.economyClass)
                backgroundDrawableId = R.drawable.vehicle_information_vehicle_background_withfirst;
            else
                backgroundDrawableId = R.drawable.vehicle_information_vehicle_background_firstonly;
        } else {
            if (vehicleData.economyClass)
                backgroundDrawableId = R.drawable.vehicle_information_vehicle_background_economy;
            else if (vehicleData.restaurant)
                backgroundDrawableId = R.drawable.vehicle_information_vehicle_background_restaurant;
            else
                backgroundDrawableId = R.drawable.vehicle_information_vehicle_background_nopax;
        }
        vehicleLayout.setBackgroundResource(backgroundDrawableId);

        final TextView wagonLabel = vehicleLayout.findViewById(R.id.vehicle_information_wagon_label);
        if (vehicleData.wagonLabel != null) {
            wagonLabel.setText(vehicleData.wagonLabel);
        } else {
            wagonLabel.setVisibility(View.GONE);
        }

        final TextView vehicleId = vehicleLayout.findViewById(R.id.vehicle_information_vehicle_id);
        if (vehicleData.vehicleIdentification != null) {
            vehicleId.setText(vehicleData.vehicleIdentification);
        } else {
            vehicleId.setVisibility(View.GONE);
        }

        if (vehicleData.bicycleSpaces != null && (showBicycle || showAllInformation)) {
            final TextView bicycleCount = vehicleLayout.findViewById(R.id.vehicle_information_vehicle_bicycle_count);
            bicycleCount.setText(String.format("%d / %d", vehicleData.bicycleSpaces.available, vehicleData.bicycleSpaces.total));
        } else {
            vehicleLayout.findViewById(R.id.vehicle_information_vehicle_bicycle).setVisibility(View.GONE);
        }

        if (vehicleData.wheelChairSpaces != null && (showWheelchair || showAllInformation)) {
            final TextView bicycleCount = vehicleLayout.findViewById(R.id.vehicle_information_vehicle_wheelchair_count);
            bicycleCount.setText(String.format("%d / %d", vehicleData.wheelChairSpaces.available, vehicleData.wheelChairSpaces.total));
        } else {
            vehicleLayout.findViewById(R.id.vehicle_information_vehicle_wheelchair).setVisibility(View.GONE);
        }

        if (vehicleData.restaurant) {
            vehicleLayout.findViewById(R.id.vehicle_information_vehicle_amenity_restaurant).setVisibility(View.VISIBLE);
        }

//        vehicleData.infoZone;
//        vehicleData.valuedCustomer;
//        vehicleData.childrenSpace;
//        vehicleData.familyZone;
//        vehicleData.quietZone;
//        vehicleData.seatsForDisabled;
//        vehicleData.toiletForWheelChair;
//        vehicleData.airCondition;
    }
}
