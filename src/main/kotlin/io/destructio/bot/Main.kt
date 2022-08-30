package io.destructio.bot

fun main(args: Array<String>){
    val apiToken = args[0] // Get token value from execution argument
    val bot = TavernBot(apiToken) // Create TavernBot object with apiToken
    bot.start() // Start DestructionBot
}