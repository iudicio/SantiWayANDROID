package com.example.santiway;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
// ИМПОРТ ДЛЯ РЕШЕНИЯ ОШИБКИ:
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class CreateFolderDialogFragment extends DialogFragment {

    public interface CreateFolderListener {
        void onFolderCreated(String folderName);
    }

    private CreateFolderListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (CreateFolderListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement CreateFolderListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.MyAlertDialogStyle);
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_create_folder, null);
        final TextInputEditText input = view.findViewById(R.id.folder_name_edit_text);

        builder.setView(view);

        builder.setPositiveButton("Создать", null);
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        // ИСПРАВЛЕНИЕ: Объявляем переменную как AlertDialog (вместо Dialog)
        final AlertDialog dialog = builder.create();

        // Переопределяем обработчик нажатия Positive Button, чтобы контролировать закрытие
        dialog.setOnShowListener(dialogInterface -> {
            // Теперь метод getButton() доступен, так как dialog объявлен как AlertDialog
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String folderName = input.getText().toString().trim();

                if (folderName.isEmpty()) {
                    Toast.makeText(getContext(), "Имя папки не может быть пустым", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Проверка на "unified_data" была в MainActivity, но лучше проверить тут:
                if (folderName.equals("unified_data")) {
                    Toast.makeText(getContext(), "Имя папки недоступно", Toast.LENGTH_SHORT).show();
                    return;
                }

                listener.onFolderCreated(folderName);

                dialog.dismiss();
            });
        });

        return dialog;
    }
}