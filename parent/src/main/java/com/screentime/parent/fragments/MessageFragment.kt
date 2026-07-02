package com.screentime.parent.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.parent.R
import com.screentime.parent.adapters.MessageAdapter
import com.screentime.parent.databinding.FragmentMessagesBinding
import com.screentime.parent.models.MessageRecord

class MessageFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private var pendingMessages: List<MessageRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = MessageAdapter(requireContext(), pendingMessages)
    }

    fun updateMessages(newMessages: List<MessageRecord>) {
        pendingMessages = newMessages
        if (_binding != null) {
            (binding.rvMessages.adapter as? MessageAdapter)?.submitList(newMessages)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
