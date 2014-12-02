#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <errno.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define _GNU_SOURCE
#include <getopt.h>

#include "common.h"

#define PORT 42380
#define BUF_SIZE 256
#define SCREENSHOT_CMD "SCREEN"

#ifdef DEBUG
FILE* logFile;
#endif


inline void Log(const char* msg)
{
#ifdef DEBUG
	if (errno != 0 && logFile == stderr)
	{
		char buf[BUF_SIZE];
		snprintf (buf, BUF_SIZE, "%s (errno=%d)", msg, errno);
		perror (buf);
		exit (1);
	}
	else	fprintf(logFile, "%s\n", msg);
	fflush (logFile);
#endif
}


volatile sig_atomic_t end = 0;
void sig_INT(int sig)	{ fprintf(logFile, "------ Caught signal %d -----\n", sig); if (sig == SIGINT || sig == SIGSEGV) end = 1; }


ssize_t Receive(int sfd, char* buf, size_t count, int flags)
{
	int c;
	size_t len = 0;

	do
	{
		c = recv(sfd, buf, count, flags);
		if (c < 0)	return c;
		if (c == 0)	return len;

		buf += c;
		len += c;
		count -= c;
	} while (count > 0);

	return len;
}

ssize_t Send(int sfd, const char* buf, ssize_t count, int flags)
{
	int c;
	size_t len = 0;

	do
	{
		c = send(sfd, buf, count, flags);
		if (c < 0)	return c;

		buf += c;
		len += c;
		count -= c;

//#ifdef DEBUG
//		char msg[BUF_SIZE];
//		snprintf (msg, BUF_SIZE, "-- Sent %d bytes (%d total, %d remaining)", c, len, count);
//		Log (msg);
//#endif
	} while (count > 0);

	return len;
}


int start_server()
{
	Log("Starting server...");
	int sfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sfd < 0)	return -1;
	Log("- Socket creation");
	
	struct sockaddr_in sin;
	sin.sin_family = PF_INET;
	sin.sin_port = htons(PORT);
	sin.sin_addr.s_addr =  htonl(INADDR_ANY);
	if (bind (sfd, (struct sockaddr*)&sin, sizeof(struct sockaddr_in)) < 0)
		return -1;
	Log("- Socket binding");

	if (listen (sfd, 5) < 0)	return -1;
	Log ("- Socket in listening mode");
	struct linger l = { 0, 0 };
	if (setsockopt(sfd, SOL_SOCKET, SO_LINGER, (const void*)&l, sizeof(struct linger)) < 0)
		return -1;
	Log("Server started.");

	// get local address and display it
	#if DEBUG
		socklen_t sin_len = sizeof(struct sockaddr_in);
		getsockname (sfd, (struct sockaddr*)&sin, &sin_len);
		char msg[BUF_SIZE];
		snprintf (msg, BUF_SIZE, "Listening on %s:%d", inet_ntoa(sin.sin_addr), PORT);
		Log (msg);
	#endif

	return sfd;
}

int setup_signals()
{
	Log("Signal handling setup");

	struct sigaction sa;

//	// handle SIGINT
//	sa.sa_handler = sig_INT;
//	sigemptyset (&sa.sa_mask);
//	sa.sa_flags = 0;
//	if (sigaction(SIGINT, &sa, NULL) < 0)	return -1;
//
//	// ignore SIGHUP
//	sa.sa_handler = SIG_IGN;
//	sigemptyset (&sa.sa_mask);
//	sa.sa_flags = 0;
//	if (sigaction(SIGHUP, &sa, NULL) < 0)	return -1;

	int i;
	for (i = 1; i < 30; ++i) {
		sa.sa_handler = sig_INT;
		sigemptyset (&sa.sa_mask);
		sa.sa_flags = 0;
		sigaction(i, &sa, NULL);
	}
	errno = 0;

	return 0;
}

int accept_client(int servfd, int** client_fd, int* client_count)
{
	Log ("Incoming client connection");

	int cfd = accept(servfd, NULL, NULL);
	if (cfd < 0)	return -1;
	Log ("- Connection accepted");

	/* check whether the client comes from local system; detach if not */
	struct sockaddr_in client_addr;
	socklen_t ca_len = sizeof(struct sockaddr_in);
	if (getpeername(cfd, (struct sockaddr*)&client_addr, &ca_len) < 0)	return -1;
	if (strcmp(inet_ntoa(client_addr.sin_addr), "127.0.0.1") != 0) {
		Log ("- Remote client detected -- closing connection.");
		shutdown (cfd, SHUT_RDWR);
		close (cfd);
		return 0;
	}

	*client_fd = (int*)realloc(*client_fd, sizeof(int) * (*client_count + 1));
	(*client_fd)[(*client_count)++] = cfd;	// (*client_fd)[...] != *client_fd[...] -- f'kin precedence ;/

	return cfd;
}

int handle_client_input(int cfd, char* fddev)
{
	Log ("Client socket signaled for input");
	struct picture pict;
	char buf[BUF_SIZE];
	int c;	
	
	/* read input and parse it */
	Log ("- Retreiving data");
	c = Receive(cfd, buf, strlen(SCREENSHOT_CMD), 0);
	if (c == 0 || (c < 0 && errno == EINTR))	return 0;
	if (c < 0)	return -1;
	if (c >= strlen(SCREENSHOT_CMD) && (buf[strlen(SCREENSHOT_CMD)] = '\0', strcmp(buf, SCREENSHOT_CMD) == 0))
	{
		Log ("- Command identified as " SCREENSHOT_CMD);

		/* screenshot command read; take screenshot and post it through socket */
		Log ("- Taking screenshot");
		if (TakeScreenshot(fddev, &pict) < 0)	return -1;
		Log ("- Screenshot taken");
		
		/* header: width height BPP */
		memset (buf, 0, BUF_SIZE * sizeof(char));
		snprintf (buf, BUF_SIZE, "%d %d %d", pict.xres, pict.yres, pict.bps);
		if (Send(cfd, buf, (strlen(buf) + 1) * sizeof(char), 0) < 0)	/* incl. \0 */
		{
			return -1;
		}
		
		Log (buf);
		Log ("- Response header sent.");

		/* content */
		int length = pict.xres * pict.yres * pict.bps / 8;
		int	seek, len;

		for(seek=0; seek<length; seek+=len) {
			len = Send(cfd, &pict.buffer[seek], length-seek, 0);
			if (len < 0)
				return -1;
		}
		free(pict.buffer);
		Log ("- Screenshot sent");
	}

	return c;
}

int cleanup(int servfd, int* client_fd, int client_count)
{
	Log ("Shutdown");
	int i;
	for (i = 0; i < client_count; ++i)
		if (close(client_fd[i]) < 0)	return -1;
	free (client_fd);

	Log ("- Closing server socket");
	if (close(servfd) < 0)	return -1;

	Log ("Shutdown complete");

#ifdef DEBUG
	if (logFile != stderr)	fclose (logFile);
#endif
	return 0;
}

int do_work(int servfd, char* fbDevice)
{
	int* client_fd = NULL;
	int client_count = 0;
	int max_fd;
	fd_set readfs;
	int i, c;

	Log ("Starting main loop.");
	while (!end)
	{
		/* fill fd_set to check sockets for reading (client and server ones) */
		Log ("<< select() on sockets... >>");
		FD_ZERO(&readfs); max_fd = 0;
		FD_SET(servfd, &readfs);
		if (servfd > max_fd)	max_fd = servfd;
		for (i = 0; i < client_count; ++i)
		{
			if (client_fd[i] < 0)	continue;

			FD_SET (client_fd[i], &readfs);
			if (client_fd[i] > max_fd)	max_fd = client_fd[i];
		}

		if (select(max_fd + 1, &readfs, NULL, NULL, NULL) == -1)
		{
			if (errno == EINTR)	{ errno = 0; continue; }
			return -1;
		}

		/* check for input on client socket and handle it */
		for (i = 0; i < client_count; ++i)
		{
			if (client_fd[i] < 0)	continue;
			if (FD_ISSET(client_fd[i], &readfs))
			{
				c = handle_client_input (client_fd[i], fbDevice);
				if (c < 0 && errno != EPIPE && errno!= ECONNRESET)	return -1;
				
				/* connection finished */
				close (client_fd[i]);
				client_fd[i] = -1;	// no socket
			}
		}

		/* check whether we have incoming connection */
		if (FD_ISSET(servfd, &readfs))
			if (accept_client (servfd, &client_fd, &client_count) < 0)
				return -1;
	}
	Log ("- Caught SIGINT");

	return cleanup(servfd, client_fd, client_count);
}


int main(int argc, char* argv [])
{
#ifdef DEBUG
	// optional logging to file
	if (argc >= 2) {
		logFile = fopen(argv[1], "a+");
	}
	else
		logFile = stderr;
#endif
	Log ("Program initialized");

	char* device;
	device = "/dev/graphics/fb0";

	int server_socket = start_server();
	if (server_socket < 0)
		Log ("Error while starting server");
	if (setup_signals() < 0)
		Log ("Error while setting up signals");

//	Log ("Going into background -- have nice day.");
//	if (setsid () < 0)	Log("Error while going into background");
	if (do_work (server_socket, device) < 0)
		Log ("Error in main loop");
}
