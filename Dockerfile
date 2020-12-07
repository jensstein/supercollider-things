FROM fedora:33

RUN dnf upgrade -y --refresh && \
	dnf install -y cmake make gcc g++ libsndfile-devel \
	libXt-devel jack-audio-connection-kit-devel qt5-qtbase-devel \
	qt5-qtsvg-devel qt5-linguist qt5-qtwebsockets-devel \
	qt5-qtwebengine-devel systemd-devel fftw-devel libatomic && \
	dnf clean all

RUN dnf install -y rpm-build rpmdevtools

RUN useradd -m supercollider
USER supercollider
WORKDIR /home/supercollider

COPY supercollider.spec supercollider.spec
COPY build-rpm-packages.sh build-rpm-packages.sh

RUN rpmdev-setuptree

CMD ["bash"]
