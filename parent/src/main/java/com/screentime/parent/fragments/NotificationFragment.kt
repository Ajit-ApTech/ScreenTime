package com.screentime.parent.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.parent.adapters.NotificationAdapter
import com.screentime.parent.databinding.FragmentListBinding
import com.screentime.parent.models.NotificationRecord
import com.screentime.parent.R

class NotificationFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter
    private var pendingNotifications: List<NotificationRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter(requireContext())
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        if (pendingNotifications.isNotEmpty()) {
            updateNotifications(pendingNotifications)
            pendingNotifications = emptyList()
        }
    }

    fun updateNotifications(newNotifications: List<NotificationRecord>) {
        if (_binding == null) {
            pendingNotifications = newNotifications
            return
        }

        if (newNotifications.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = getString(R.string.no_notifications_found_for_today)
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
            adapter.submitList(newNotifications)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}