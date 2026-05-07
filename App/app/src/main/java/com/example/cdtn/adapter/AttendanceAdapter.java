package com.example.cdtn.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cdtn.R;
import com.example.cdtn.model.StudentAttendanceResponse;

import java.util.List;

public class AttendanceAdapter
        extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private final List<StudentAttendanceResponse> list;

    public AttendanceAdapter(List<StudentAttendanceResponse> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                         int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder,
                                 int position) {

        StudentAttendanceResponse item = list.get(position);

        holder.txtClass.setText(item.getClassName());
        holder.txtSubject.setText(item.getSubjectName());
        holder.txtTime.setText(item.getAttendanceTime());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtClass, txtSubject, txtTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtClass = itemView.findViewById(R.id.txtClassName);
            txtSubject = itemView.findViewById(R.id.txtSubject);
            txtTime = itemView.findViewById(R.id.txtTime);
        }
    }
}