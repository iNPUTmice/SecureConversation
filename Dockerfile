FROM ksoichiro/android

# Install ant & SDK v19
RUN apt-get install -y ant
RUN echo y | android update sdk --filter android-19 --no-ui --force

# Add & build Conversations
ADD . /Conversations
WORKDIR /Conversations
RUN git submodule update --init --recursive
RUN ant clean && ant debug

# No run CMD, just get the apk build:
CMD /bin/true
