#!/bin/sh
# Build a statically linked aarch64 htop for Nanaya.
# Requires: musl-tools, make, wget, tar, xz.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${WORK:-/tmp/nanaya-htop}
PREFIX="$WORK/musl"
NCURSES=6.5
HTOP=3.5.3

mkdir -p "$WORK" "$PREFIX"
cd "$WORK"

[ -f "ncurses-$NCURSES.tar.gz" ] || \
  wget -O "ncurses-$NCURSES.tar.gz" "https://invisible-mirror.net/archives/ncurses/ncurses-$NCURSES.tar.gz"
[ -d "ncurses-$NCURSES" ] || tar xf "ncurses-$NCURSES.tar.gz"
cd "ncurses-$NCURSES"
CC=musl-gcc ./configure \
  --prefix="$PREFIX" \
  --disable-shared --enable-static --enable-widec \
  --with-fallbacks=xterm-256color,screen-256color \
  --without-manpages --without-debug --without-ada --without-cxx --without-cxx-binding
make -j"$(nproc)"
make install
cd "$WORK"

[ -f "htop-$HTOP.tar.xz" ] || \
  wget -O "htop-$HTOP.tar.xz" "https://github.com/htop-dev/htop/releases/download/$HTOP/htop-$HTOP.tar.xz"
[ -d "htop-$HTOP" ] || tar xJf "htop-$HTOP.tar.xz"
cd "htop-$HTOP"
PATH="$PREFIX/bin:$PATH" \
PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" \
CC=musl-gcc CFLAGS="-O2 -idirafter /usr/include -idirafter /usr/include/aarch64-linux-gnu" LDFLAGS="-static -L$PREFIX/lib" \
./configure \
  --prefix="$PREFIX" \
  --enable-static
make -j"$(nproc)"

mkdir -p "$ROOT/app/src/main/assets/htop"
aarch64-linux-gnu-strip htop
cp htop "$ROOT/app/src/main/assets/htop/htop-aarch64"
chmod 644 "$ROOT/app/src/main/assets/htop/htop-aarch64"
file "$ROOT/app/src/main/assets/htop/htop-aarch64"
