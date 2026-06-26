package com.screentime.kids.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.kids.R
import com.screentime.kids.adapters.AppUsageAdapter
import com.screentime.kids.databinding.FragmentAppUsageBinding
import com.screentime.kids.models.AppSession

class AppUsageFragment : Fragment() {

    private var _binding: FragmentAppUsageBinding? = null
    private val binding get() = _binding!!

    // Buffer data in case activity delivers it before the view is created
    private var pendingSessions: List<AppSession> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppUsage.adapter = AppUsageAdapter(requireContext(), pendingSessions)
    }

    fun updateAppSessions(newSessions: List<AppSession>) {
        pendingSessions = newSessions
        if (_binding != null) {
            (binding.rvAppUsage.adapter as? AppUsageAdapter)?.submitList(newSessions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
