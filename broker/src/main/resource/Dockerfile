FROM java:8
VOLUME /tmp
ADD octopus-0.12.1.zip push.zip
RUN apt-get install unzip
RUN unzip push.zip
EXPOSE 1883
CMD /octopus-0.12.1/bin/octopus.sh
