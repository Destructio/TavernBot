package io.destructio.bot

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import io.destructio.bot.audio.TrackScheduler
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import io.destructio.bot.models.Command
import java.util.*
import kotlin.collections.HashMap
import java.util.concurrent.atomic.AtomicBoolean

class TavernBot(private val apiToken: String) {
    private val commands: MutableMap<String, Command> = HashMap() // Create a list of commands
    private val playerManager = DefaultAudioPlayerManager() // Create AudioPlayer instances and translates URLs to AudioTrack instances
    private val player = playerManager.createPlayer()  // Create an AudioPlayer so Discord4J can receive audio data
    private var bot = DiscordClientBuilder.create(apiToken).build().login().block()!! // Create DiscordClient for bot

    fun start(){
        configurePlayer()

        bot.eventDispatcher.on(MessageCreateEvent::class.java)
            /* subscribe is like block, in that it will *request* for action
             to be done, but instead of blocking the thread, waiting for it
             to finish, it will just execute the results asynchronously.*/
            .subscribe {
                    event: MessageCreateEvent -> newMessageReact(event)
            }

        stop("Normal stop.")
    }

    fun stop(reason: String) {
        println("Closing Bot. Reason: $reason")
        bot.onDisconnect().block()
    }

    private fun newMessageReact(event: MessageCreateEvent){

        val message = event.message.content
        val username = event.message.author.get().username

        if(message.substring(0) == "!") {
            for ((key, value) in commands) {
                if (message.startsWith("!$key")) {
                    println("Query from $username - Message: $message")
                    value.execute(event)
                    break
                }
            }
        }
        else if ((message.startsWith(">rs") || message.startsWith(">recent")))
        {
            event.message.delete().block()
        }

        else
        {
            // TODO: Antispam protection
        }


    }

    private fun configurePlayer() {
        println("Starting the TavernBot with token: $apiToken")

        // This is an optimization strategy that Discord4J can utilize. It is not important to understand
        playerManager.configuration.frameBufferFactory = AudioFrameBufferFactory {
                bufferDuration: Int, format: AudioDataFormat?, stopping: AtomicBoolean? ->
            NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
        }

        // Allow playerManager to parse remote sources like YouTube links
        AudioSourceManagers.registerRemoteSources(playerManager)
    }

    init {
        commands["ping"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                event.message
                    .channel.block()!!
                    .createMessage("pong!")
                    .block()
            }
        }
        commands["roll"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                val content = event.message.content
                val command = listOf(*content.split(" ").toTypedArray())
                val out = when (command.size) {
                    1 -> Random().nextInt(100 + 1).toString()
                    2 -> Random().nextInt(command[1].toInt() + 1).toString()
                    else -> "Please enter correct range! e.g !roll 100"
                }
                event.message.channel
                    .block()!!
                    .createMessage(out)
                    .block()
            }
        }
        commands["help"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                event.message
                    .channel.block()!!
                    .createMessage(
                        "More information about bot you can find here: " +
                                "https://github.com/Destructio/TavernBot/blob/master/README.md"
                    )
                    .block()
            }
        }
        commands["join"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                val member = event.member.orElse(null)
                val voiceState = member.voiceState.block()!!
                val channel: VoiceChannel = voiceState.channel.block()!!
                channel.join().block()
            }
        }
        val scheduler = TrackScheduler(player)
        commands["play"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                val content = event.message.content
                val command = listOf(*content.split(" ").toTypedArray())
                playerManager.loadItem(command[1], scheduler)
            }
        }
        commands["stop"] = object : Command {
            override fun execute(event: MessageCreateEvent) {
                player.stopTrack()
            }
        }
    }
}