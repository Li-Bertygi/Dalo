package com.Libertygi.dalo

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 에러 메시지를 사용자에게 보여주는 커스텀 다이얼로그 프래그먼트입니다.
 * 단순 토스트 메시지보다 명확하게 오류 상황을 전달하고, 사용자가 확인 버튼을 눌러야만 닫히도록 설계되었습니다.
 */
class ErrorDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // 커스텀 레이아웃(dialog_error.xml)을 인플레이트합니다.
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_error, null)

        // 전달받은 에러 메시지를 가져와 텍스트뷰에 설정합니다.
        val message = requireArguments().getString(ARG_MSG).orEmpty()
        v.findViewById<TextView>(R.id.tvMessage).text = message

        // 닫기 버튼(X 아이콘 등) 클릭 시 다이얼로그를 닫습니다.
        v.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { dismiss() }

        // 다이얼로그 바깥 영역을 터치하거나 뒤로가기 버튼을 눌러도 닫히지 않도록 설정합니다 (모달 동작).
        isCancelable = false

        // Material Design 스타일의 알림 대화상자를 생성합니다.
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .create()

        // 다이얼로그가 표시될 때 윈도우 속성을 설정합니다.
        // 배경을 어둡게 처리(Dim)하여 팝업에 집중하도록 만듭니다.
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window?.setDimAmount(0.55f)
        }
        return dialog
    }

    companion object {
        // 번들에 메시지를 저장할 때 사용할 키 상수
        private const val ARG_MSG = "msg"

        /**
         * ErrorDialogFragment의 새 인스턴스를 생성하는 팩토리 메서드입니다.
         * 생성자를 직접 호출하는 대신 이 메서드를 사용하여 에러 메시지를 안전하게 전달합니다.
         *
         * @param msg 표시할 에러 메시지
         * @return 설정된 ErrorDialogFragment 인스턴스
         */
        fun newInstance(msg: String) = ErrorDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MSG, msg) }
        }
    }
}