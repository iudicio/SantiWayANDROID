package com.example.santiway;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class FolderSelectionBottomSheet extends BottomSheetDialogFragment {

    // Интерфейс для передачи результата обратно в Activity
    public interface FolderSelectionListener {
        void onFolderSelected(String folderName);
    }

    private final List<String> folders;
    private final FolderSelectionListener listener;

    public FolderSelectionBottomSheet(List<String> folders, FolderSelectionListener listener) {
        this.folders = folders;
        this.listener = listener;
        // Устанавливаем тему для BottomSheet, чтобы избежать проблем с прозрачностью/стилем
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.AppBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Используем кастомный макет для Bottom Sheet
        View view = inflater.inflate(R.layout.bottom_sheet_folder_selection, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_folders);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Передаем адаптеру обработчик кликов
        recyclerView.setAdapter(new FolderAdapter(folders, folderName -> {
            if (listener != null) {
                listener.onFolderSelected(folderName);
            }
            dismiss(); // Закрываем диалог после выбора
        }));

        return view;
    }

    // --- Вложенный класс для Адаптера RecyclerView ---
    private static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {
        private final List<String> folderList;
        private final OnFolderClickListener clickListener;

        interface OnFolderClickListener {
            void onFolderClick(String folderName);
        }

        public FolderAdapter(List<String> folderList, OnFolderClickListener clickListener) {
            this.folderList = folderList;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Используем макет для отдельного элемента списка
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_name, parent, false);
            return new FolderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            String folderName = folderList.get(position);
            holder.folderNameTextView.setText(folderName);
            holder.itemView.setOnClickListener(v -> clickListener.onFolderClick(folderName));
        }

        @Override
        public int getItemCount() {
            return folderList.size();
        }

        static class FolderViewHolder extends RecyclerView.ViewHolder {
            TextView folderNameTextView;

            public FolderViewHolder(@NonNull View itemView) {
                super(itemView);
                // ID для названия папки в макете item_folder_name.xml
                folderNameTextView = itemView.findViewById(R.id.folder_name_text);
            }
        }
    }
}