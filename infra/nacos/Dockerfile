FROM nacos/nacos-server:v3.2.4
COPY patch-console.py /tmp/patch-console.py
RUN /home/nacos/.venv/bin/python3 /tmp/patch-console.py /home/nacos/target/nacos-server.jar \
    && rm /tmp/patch-console.py
