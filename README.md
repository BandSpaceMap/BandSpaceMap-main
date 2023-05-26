# Beatsaver

[![Build Status](https://jenkins.kirkstall.top-cat.me/buildStatus/icon?job=Main)](https://jenkins.kirkstall.top-cat.me/view/Beatsaver/job/Main/)

The main codebase for the beatsaver website.

Contains both backend code for uploading and managing maps, the beatsaver API and a reactJS frontend all written in Kotlin with shared code.

## Local setup

#### Prerequisites
- [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) (Community Edition is fine)
- [Docker Desktop](https://www.docker.com/products/docker-desktop)

#### Lets go
- Run `docker-compose up -d` to start the local database and RABBITMQ
- Navigate to http://localhost:15672/
- Login with 
  - `bandspace:bandspaceRabbitmq`
- Create a new virtual host called `bandspace`
- Open the project in IntelliJ IDEA
- Run the `run` gradle task
- Navigate to http://localhost:8080
- Login with one of the test users
  - admin:admin
  - test:test


#### Extra environment variables
- `AUDIO_DIR` Directory preview audio will get served from
- `AVATAR_DIR` Directory avatars will get served from
- `ZIP_DIR` Directory zips will get served from
- `COVER_DIR` Directory cover images will get served from
- `PLAYLIST_COVER_DIR` Directory playlist covers will get served from
- `UPLOAD_DIR` Directory files get uploaded to before being processed
- `BASE_URL` The base url of the website
  - example: `http://localhost:8080`
- `BASE_API_URL` The base url of the api
  - example: `http://localhost:8080/api`
- `HOSTNAME` The hostname of the server
  - example: `BandSpace`
- `RABBITMQ_HOST` The hostname of the rabbitmq server
  - example: `127.0.0.1`
- `RABBITMQ_PORT` The port of the rabbitmq server
  - example: `5672`
- `RABBITMQ_USER` The username of the rabbitmq server
  - example: `bandspace`
- `RABBITMQ_PASS` The password of the rabbitmq server
  - example: `bandspaceRabbitmq`
- `RABBITMQ_VHOST` The vhost of the rabbitmq server
  - example: `bandspace`
- `RELAY_HOSTNAME` The hostname of the relay server use SMTP server
  - example: `smtp.gmail.com`
- `RELAY_USERNAME` The username of the relay server use SMTP server
- `RELAY_PASSWORD` The password of the relay server use SMTP server

Zips, covers and audio files must be placed in a subfolder that is named with their first character

e.g. `cb9f1581ff6c09130c991db8823c5953c660688f.zip` must be in `$ZIP_DIR/c/cb9f1581ff6c09130c991db8823c5953c660688f.zip`

## Code Style

This project uses ktlint which provides fairly basic style rules.

You can run the `ktlintFormat` gradle task to automatically apply most of them or the `ktlintCheck` task to get a list of linting errors.

For example, on Windows, navigate to the project's root directory in CMD.
Execute "gradlew.bat ktlintCheck".
if successful, you should see "BUILD SUCCESSFUL" in the console.
If not, you will see a list of linting errors.
Then execute "gradlew.bat run" to start the server.
