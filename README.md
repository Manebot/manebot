<p align="center">
  <img height="128" src="https://github.com/Manevolent/manebot/raw/master/manebot.png">
  <br/>
  <a href="https://discord.gg/qJPzQX3"><img height="28" src="https://img.shields.io/discord/563010101254815776.svg?label=Discord&logo=discord&style=for-the-badge"></a> <img height="28" src="https://img.shields.io/github/issues/manevolent/manebot.svg?style=for-the-badge">
</p>

**Manebot** is a a plugin-based chatbot framework in Java, supporting a variety of *chat platforms*. The goal of this project is to provide a multi-platform API for both developers and community leads to offer cool, automated features across all their online communities at the same time.

### Community leads

Do you have a community, such a Discord server, Skype group, IRC, and/or Teamspeak 3 server? Manebot can provide you with a platform to host an automated bot system across all of your platforms that your community exists on. It's easy to run, and has a docker container to get started quickly. Manebot comes with a command-line interface that allows you to set up and configure your bot from scratch with no other dependencies and without messing around with files.

**Docker image**: https://hub.docker.com/r/manevolent/manebot

**Supported platforms**

| **Platform** 	| **Supported Features**              	| **Installation**         	|
|--------------	|-------------------------------------	|--------------------------	|
| Discord      	| Full text and audio support         	| `plugin install discord` 	|
| Teamspeak 3  	| Coming soon: text and audio support 	| `plugin install ts3`     	|
| Slack        	| Coming soon: full text support      	| `plugin install slack`   	|

### Developers

You can avoid tracking multiple code-streams for each of your bot's platforms, and centralize your codebase in one place by rebasing to Manebot. You can also bring your bots into other platforms you haven't developed for yet by building your next bot on Manebot. When you use Manebot, your features are immediately available in the entire universe of supported platforms.

* JavaDoc: https://manevolent.github.io/manebot-core/

## Plugins

Since Manebot is open-source, anyone can make a plugin for Manebot. As part of the project, Manebot has some officially developed plugins that are also open-source.

#### Plugin dependency

Plugins can extend other plugins, adding to the depender's feature set and reducing code duplication. For example, the **discord** plugin depends on **audio** to provide music bot capabilites in the platform it in turn provides, and **audio** depends on **media** to support media transcoding, among other neat stuff. Depending a plugin is easy-peasy: since Manebot uses **Maven** as its engine for dependency graph building, all you have to do is add a plugin as a *provided* dependency in your Maven project. Manebot will automatically associate these as required dependencies, and handle the rest for you.

## Platforms

Manebot considers a **platform** as a online communication service such as Discord, Skype, Teamspeak, and so forth. Manebot is platform-agnostic; this means that it can support many platforms at once, providing as many features of Manebot to those platforms as possible. Adding a specific platform to Manebot is as simple as:
```
plugin install discord
```
