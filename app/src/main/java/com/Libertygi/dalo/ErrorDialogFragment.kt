package com.Libertygi.dalo

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ErrorDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_error, null)

        val message = requireArguments().getString(ARG_MSG).orEmpty()
        v.findViewById<TextView>(R.id.tvMessage).text = message
        v.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { dismiss() }

        // 밖 터치/뒤로가기 막기 (원하면 뒤로는 허용 가능)
        isCancelable = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .create()

        // 뒷부분 흐리게 + 터치 차단은 다이얼로그가 기본으로 해주고,
        // dim 정도만 명시해줌
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window?.setDimAmount(0.55f)
        }
        return dialog
    }

    companion object {
        private const val ARG_MSG = "msg"

        fun newInstance(msg: String) = ErrorDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MSG, msg) }
        }
    }
}
