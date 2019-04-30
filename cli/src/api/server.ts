/*
 [The "BSD licence"]
 Copyright (c) 2019 Kevin Jones
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import * as java from 'java'
import * as fs from 'fs'
import * as path from 'path'
import Org from './org';

export default class Server {

    private static instance: Server

    private constructor() {
        if (Server.instance) {
            throw new Error('Server instance already running - use Server.getInstance()')
        }
    }

    static async getInstance(): Promise<Server> {
        return new Promise<Server>(function (resolve, reject) {
            if (!Server.instance) {
                java.classpath.push(Server.jarHome())
                java.ensureJvm(function (err, result) {
                    if (err) {
                        reject(err)
                    } else {
                        if (!Server.instance)
                            Server.instance = new Server()
                        resolve(Server.instance)
                    }
                })
            } else {
                resolve(Server.instance)
            }
        });
    }

    public createOrg(): Org {
        return new Org()
    }

    public setLoggingLevel(verbose: boolean) {
        java.callStaticMethodSync('com.nawforce.api.LogUtils', 'setLoggingLevel', verbose)
    }

    private static jarHome(): string {
        const jarFile = path.join(__dirname, '..', '..', 'jars', 'apexlink-0.2.jar')
        if (!fs.existsSync(jarFile) || !fs.lstatSync(jarFile).isFile()) {
            throw new Error(`No jar found at '${jarFile}'.`);
        }
        return jarFile
    }
}