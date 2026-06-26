package com.screentime.kids.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.kids.R
import com.screentime.kids.adapters.CallLogAdapter
import com.screentime.kids.databinding.FragmentCallLogBinding
import com.screentime.kids.models.CallRecord

class CallLogFragment : Fragment() {

    private var _binding: FragmentCallLogBinding? = null
    private val binding get() = _binding!!

    private var pendingCalls: List<CallRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCallLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvCallLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCallLog.adapter = CallLogAdapter(requireContext(), pendingCalls)
    }

    fun updateCallLogs(newCalls: List<CallRecord>) {
        pendingCalls = newCalls
        (binding.rvCallLog.adapter as? CallLogAdapter)?.submitList(newCalls)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
