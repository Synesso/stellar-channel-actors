# Stellar Channel with Actors

Channels are a design pattern that uses proxy accounts to facilitate higher throughput transactions.

`stellar.channel.PayWithChannels` demonstrates using a channel of 24 proxy accounts to issue 2400 payments.

`stellar.channel.PayDirectly` demonstrates how using a single channel to attempt to send just 32 payments fails.

A detailed write-up of this project is on [Lumenauts](https://www.lumenauts.com/blog/boosting-tps-with-stellar-channels)
