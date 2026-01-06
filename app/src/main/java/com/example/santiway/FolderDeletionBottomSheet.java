package com.example.santiway;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class FolderDeletionBottomSheet extends BottomSheetDialogFragment {

    public interface FolderDeletionListener {
        void onDeleteRequested(String folderName);
    }

    private static final String ARG_FOLDERS = "folders_list";
    private List<String> folders;
    private FolderDeletionListener listener;

    public FolderDeletionBottomSheet(List<String> folders, FolderDeletionListener listener) {
        this.folders = folders;
        this.listener = listener;
        if (listener == null) {
            throw new IllegalArgumentException("FolderDeletionListener cannot be null");
        }
    }

    public FolderDeletionBottomSheet() {}

    public static FolderDeletionBottomSheet newInstance(ArrayList<String> folders, FolderDeletionListener listener) {
        FolderDeletionBottomSheet fragment = new FolderDeletionBottomSheet(folders, listener);
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_FOLDERS, folders);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AppBottomSheetDialogTheme);
        if (getArguments() != null) {
            folders = getArguments().getStringArrayList(ARG_FOLDERS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_folder_list, container, false);

        TextView title = view.findViewById(R.id.bottom_sheet_title);
        title.setText("Выберите папку для удаления");

        ListView listView = view.findViewById(R.id.folder_list_view);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                requireContext(),
                R.layout.item_folder_deletion,
                R.id.folder_name_text,
                folders)
        {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View item = super.getView(position, convertView, parent);
                TextView folderNameView = item.findViewById(R.id.folder_name_text);

                folderNameView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
                return item;
            }
        };

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedFolder = folders.get(position);
            showConfirmDeleteDialog(selectedFolder);
        });

        return view;
    }

    private void showConfirmDeleteDialog(String folderName) {
        new AlertDialog.Builder(requireContext(), R.style.MyAlertDialogStyle) // Используем MyAlertDialogStyle для стилизации
                .setTitle("Подтверждение удаления")
                .setMessage("Вы уверены, что хотите удалить папку '" + folderName + "'? Все данные будут потеряны!")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    listener.onDeleteRequested(folderName);
                    dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}