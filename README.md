# chat

Simple chat system, with two projects: A server and a client.

The server maintains a list of connected clients and their IP addresses and public ports. The clients should keep pinging the server to be kept alive.

At every change in the list, the server notifies all its clients, so they can update their lists and interfaces. The clients can then communicate between themselves by P2P.

All of the communication follows a protocol developed for this project, since all messages are delivered as Strings via sockets.
