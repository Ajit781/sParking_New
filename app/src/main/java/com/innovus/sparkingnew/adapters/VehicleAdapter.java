package com.innovus.sparkingnew.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.innovus.sparkingnew.R;
import com.innovus.sparkingnew.models.ParkedVehicle;

import java.util.List;

public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.ViewHolder> {

    public interface OnVehicleActionListener {
        void onCheckoutRequested(ParkedVehicle vehicle, int position);
    }

    private final List<ParkedVehicle> list;
    private final OnVehicleActionListener listener;

    public VehicleAdapter(List<ParkedVehicle> list, OnVehicleActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parked_vehicle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ParkedVehicle item = list.get(position);
        if (item == null) return;

        holder.tvVehicleNumber.setText(item.getVehicleNumber());

        String slot = (item.getSlotName() == null || item.getSlotName().trim().isEmpty())
                ? "General Slot"
                : "Slot: " + item.getSlotName().trim();
        String rawType = (item.getVehicleType() == null || item.getVehicleType().trim().isEmpty())
                ? (item.isTwoWheeler() ? "2-Wheeler" : "4-Wheeler")
                : item.getVehicleType().trim();
        String compactType = rawType.replace("Four Wheeler", "4-Wheeler")
                                    .replace("Two Wheeler", "2-Wheeler")
                                    .replace("four wheeler", "4-Wheeler")
                                    .replace("two wheeler", "2-Wheeler");
        holder.tvSlotType.setText(slot + " • " + compactType);

        String timeStr = item.getFormattedTime();
        if (timeStr == null || timeStr.isEmpty()) {
            timeStr = "Active";
        }
        StringBuilder details = new StringBuilder();
        details.append("In: ").append(timeStr);
        if (item.getOwnerName() != null && !item.getOwnerName().trim().isEmpty()) {
            details.append(" • ").append(item.getOwnerName().trim());
        }
        holder.tvDetails.setText(details.toString());

        if (holder.tvStatus != null) {
            String status = item.getStatus();
            if (status == null || status.trim().isEmpty()) {
                status = "PARKED";
            }
            holder.tvStatus.setText(status.toUpperCase());
        }

        if (item.isTwoWheeler()) {
            holder.ivIcon.setImageResource(R.drawable.ic_two_wheeler);
            holder.ivIcon.setColorFilter(Color.parseColor("#D97706")); // Warm Amber
            if (holder.flIconContainer != null) {
                holder.flIconContainer.setBackgroundResource(R.drawable.bg_badge_2w);
            }
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_directions_car);
            holder.ivIcon.setColorFilter(Color.parseColor("#2563EB")); // Royal Blue
            if (holder.flIconContainer != null) {
                holder.flIconContainer.setBackgroundResource(R.drawable.bg_badge_4w);
            }
        }

        // Tap card
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onCheckoutRequested(item, pos);
                }
            }
        });

        // Tap Checkout button
        if (holder.btnCheckout != null) {
            holder.btnCheckout.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onCheckoutRequested(item, pos);
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public ParkedVehicle getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View flIconContainer;
        ImageView ivIcon;
        TextView tvVehicleNumber;
        TextView tvSlotType;
        TextView tvDetails;
        TextView tvStatus;
        View btnCheckout;

        ViewHolder(View itemView) {
            super(itemView);
            flIconContainer = itemView.findViewById(R.id.fl_icon_container);
            ivIcon = itemView.findViewById(R.id.iv_vehicle_icon);
            tvVehicleNumber = itemView.findViewById(R.id.tv_item_vehicle_number);
            tvSlotType = itemView.findViewById(R.id.tv_item_slot_type);
            tvDetails = itemView.findViewById(R.id.tv_item_details);
            tvStatus = itemView.findViewById(R.id.tv_item_status);
            btnCheckout = itemView.findViewById(R.id.btn_item_checkout);
        }
    }
}
