package com.screentime.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.admin.adapters.AppUsageAdminAdapter
import com.screentime.admin.adapters.CallLogAdminAdapter
import com.screentime.admin.adapters.MessageAdminAdapter
import com.screentime.admin.adapters.NotificationAdminAdapter
import com.screentime.admin.databinding.FragmentAdminListBinding
import com.screentime.admin.models.AppSession
import com.screentime.admin.models.CallRecord
import com.screentime.admin.models.MessageRecord
import com.screentime.admin.models.NotificationRecord

class AppUsageAdminFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!

    private var pendingSessions: List<AppSession> = emptyList()
    var onEditSession: ((AppSession) -> Unit)? = null
    var onDeleteSession: ((AppSession) -> Unit)? = null
    var onSessionClick: ((AppSession) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = AppUsageAdminAdapter(
            onEditUsage = { session -> onEditSession?.invoke(session) },
            onDeleteUsage = { session -> onDeleteSession?.invoke(session) },
            onAppClick = { session -> onSessionClick?.invoke(session) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        updateList(pendingSessions)
    }

    fun submitData(sessions: List<AppSession>) {
        pendingSessions = sessions
        if (_binding != null) {
            updateList(sessions)
        }
    }

    private fun updateList(sessions: List<AppSession>) {
        val adapter = binding.recyclerView.adapter as? AppUsageAdminAdapter
        adapter?.submitList(sessions)

        if (sessions.isEmpty()) {
            binding.tvEmpty.text = "No app usage recorded"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CallLogAdminFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!

    private var pendingCalls: List<CallRecord> = emptyList()
    var onEditCall: ((CallRecord) -> Unit)? = null
    var onDeleteCall: ((CallRecord) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CallLogAdminAdapter(
            onEditCall = { call -> onEditCall?.invoke(call) },
            onDeleteCall = { call -> onDeleteCall?.invoke(call) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        updateList(pendingCalls)
    }

    fun submitData(calls: List<CallRecord>) {
        pendingCalls = calls
        if (_binding != null) {
            updateList(calls)
        }
    }

    private fun updateList(calls: List<CallRecord>) {
        val adapter = binding.recyclerView.adapter as? CallLogAdminAdapter
        adapter?.submitList(calls)

        if (calls.isEmpty()) {
            binding.tvEmpty.text = "No call logs recorded"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MessageAdminFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!

    private var pendingMsgs: List<MessageRecord> = emptyList()
    var onDeleteMsg: ((MessageRecord) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = MessageAdminAdapter(
            onDeleteMsg = { msg -> onDeleteMsg?.invoke(msg) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        updateList(pendingMsgs)
    }

    fun submitData(messages: List<MessageRecord>) {
        pendingMsgs = messages
        if (_binding != null) {
            updateList(messages)
        }
    }

    private fun updateList(messages: List<MessageRecord>) {
        val adapter = binding.recyclerView.adapter as? MessageAdminAdapter
        adapter?.submitList(messages)

        if (messages.isEmpty()) {
            binding.tvEmpty.text = "No message records"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class NotificationAdminFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!

    private var pendingNotifs: List<NotificationRecord> = emptyList()
    var onDeleteNotif: ((NotificationRecord) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = NotificationAdminAdapter(
            onDeleteNotif = { notif -> onDeleteNotif?.invoke(notif) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        updateList(pendingNotifs)
    }

    fun submitData(notifs: List<NotificationRecord>) {
        pendingNotifs = notifs
        if (_binding != null) {
            updateList(notifs)
        }
    }

    private fun updateList(notifs: List<NotificationRecord>) {
        val adapter = binding.recyclerView.adapter as? NotificationAdminAdapter
        adapter?.submitList(notifs)

        if (notifs.isEmpty()) {
            binding.tvEmpty.text = "No notifications captured"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
