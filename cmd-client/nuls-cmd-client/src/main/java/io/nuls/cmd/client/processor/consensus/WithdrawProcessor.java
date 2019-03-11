/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.nuls.cmd.client.processor.consensus;


import io.nuls.api.provider.Result;
import io.nuls.api.provider.ServiceManager;
import io.nuls.api.provider.consensus.ConsensusProvider;
import io.nuls.api.provider.consensus.facade.WithdrawReq;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.NulsDigestData;
import io.nuls.cmd.client.CommandBuilder;
import io.nuls.cmd.client.CommandHelper;
import io.nuls.cmd.client.CommandResult;
import io.nuls.cmd.client.Config;
import io.nuls.cmd.client.processor.CommandProcessor;
import io.nuls.tools.core.annotation.Autowired;
import io.nuls.tools.core.annotation.Component;
import lombok.extern.slf4j.Slf4j;

import static io.nuls.cmd.client.CommandHelper.getPwd;

/**
 * @author: zhoulijun
 */
@Component
@Slf4j
public class WithdrawProcessor implements CommandProcessor {

    @Autowired
    Config config;

    ConsensusProvider consensusProvider = ServiceManager.get(ConsensusProvider.class);

    @Override
    public String getCommand() {
        return "withdraw";
    }

    @Override
    public String getHelp() {
        CommandBuilder bulider = new CommandBuilder();
        bulider.newLine(getCommandDescription())
                .newLine("\t<address>   address -required")
                .newLine("\t<txHash>    your deposit transaction hash  -required");
        return bulider.toString();
    }

    @Override
    public String getCommandDescription() {
        return "withdraw <address> <txHash> -- withdraw the agent";
    }

    @Override
    public boolean argsValidate(String[] args) {
        int length = args.length;
        if(length != 3){
            return false;
        }
        if (!CommandHelper.checkArgsIsNull(args)) {
            return false;
        }
        if(!AddressTool.validAddress(config.getChainId(),args[1]) || !NulsDigestData.validHash(args[2])){
            return false;
        }
        return true;
    }

    @Override
    public CommandResult execute(String[] args) {
        String address = args[1];
        String txHash = args[2];
        String password = getPwd("Enter your account password");
        Result<String> result = consensusProvider.withdraw(new WithdrawReq(address,txHash,password));
        if (result.isFailed()) {
            return CommandResult.getFailed(result);
        }
        return CommandResult.getSuccess(result);
    }
}