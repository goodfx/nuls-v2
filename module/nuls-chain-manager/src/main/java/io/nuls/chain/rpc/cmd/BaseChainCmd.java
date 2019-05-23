/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
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
package io.nuls.chain.rpc.cmd;

import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.TransactionFeeCalculator;
import io.nuls.base.data.CoinData;
import io.nuls.base.data.CoinFrom;
import io.nuls.base.data.CoinTo;
import io.nuls.base.data.Transaction;
import io.nuls.base.signture.P2PHKSignature;
import io.nuls.chain.info.CmConstants;
import io.nuls.chain.info.CmErrorCode;
import io.nuls.chain.info.CmRuntimeInfo;
import io.nuls.chain.model.dto.AccountBalance;
import io.nuls.chain.model.po.Asset;
import io.nuls.chain.model.po.BlockChain;
import io.nuls.chain.model.tx.txdata.TxChain;
import io.nuls.chain.util.LoggerUtil;
import io.nuls.core.rpc.cmd.BaseCmd;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.core.rpc.util.RPCUtil;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.BigIntegerUtils;
import io.nuls.core.model.ByteUtils;
import io.nuls.core.model.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * @author lan
 * @date 2018/11/28
 */
public class BaseChainCmd extends BaseCmd {


    boolean isMainAsset(String assetKey) {
        return CmRuntimeInfo.getMainAssetKey().equals(assetKey);
    }

    Response parseTxs(List<String> txHexList, List<Transaction> txList) {
        for (String txHex : txHexList) {
            if (StringUtils.isBlank(txHex)) {
                return failed("txHex is blank");
            }
            byte[] txStream = RPCUtil.decode(txHex);
            Transaction tx = new Transaction();
            try {
                tx.parse(new NulsByteBuffer(txStream));
                txList.add(tx);
            } catch (NulsException e) {
                LoggerUtil.logger().error("transaction parse error", e);
                return failed("transaction parse error");
            }
        }
        return success();
    }

    /**
     * 注册链或资产封装coinData,x%资产进入黑洞，y%资产进入锁定
     */
    CoinData getRegCoinData(byte[] address, int chainId, int assetsId, String amount,
                            int txSize, AccountBalance accountBalance,String lockRate) throws NulsRuntimeException {
        txSize = txSize + P2PHKSignature.SERIALIZE_LENGTH;
        CoinData coinData = new CoinData();
        BigInteger lockAmount = new BigDecimal(amount).multiply(new BigDecimal(lockRate)).toBigInteger();
        BigInteger destroyAmount = new BigInteger(amount).subtract(lockAmount);
        CoinTo to1 = new CoinTo(address, chainId, assetsId, lockAmount, -1);
        CoinTo to2 = new CoinTo(CmConstants.BLACK_HOLE_ADDRESS, chainId, assetsId, destroyAmount, 0);
        coinData.addTo(to1);
        txSize += to1.size();
        coinData.addTo(to2);
        txSize += to2.size();
        //手续费
        CoinFrom from = new CoinFrom(address, chainId, assetsId, new BigDecimal(amount).toBigInteger(), accountBalance.getNonce(), (byte) 0);
        txSize += from.size();
        BigInteger fee = TransactionFeeCalculator.getNormalTxFee(txSize);
        String fromAmount = BigIntegerUtils.addToString(amount, fee.toString());
        if (BigIntegerUtils.isLessThan(accountBalance.getAvailable(), fromAmount)) {
            throw new NulsRuntimeException(CmErrorCode.BALANCE_NOT_ENOUGH);
        }
        from.setAmount(BigIntegerUtils.stringToBigInteger(fromAmount));
        coinData.addFrom(from);
        return coinData;
    }

    /**
     * 注销资产进行处理
     */
    CoinData getDisableCoinData(byte[] address, int chainId, int assetsId, String amount,
                                int txSize, String txHash, AccountBalance accountBalance,String lockRate) throws NulsRuntimeException {
        txSize = txSize + P2PHKSignature.SERIALIZE_LENGTH;

        BigInteger lockAmount = new BigDecimal(amount).multiply(new BigDecimal(lockRate)).toBigInteger();
        CoinTo to = new CoinTo(address, chainId, assetsId, lockAmount, 0);

        CoinData coinData = new CoinData();
        coinData.addTo(to);
        txSize += to.size();
        //手续费
        CoinFrom from = new CoinFrom(address, chainId, assetsId, new BigInteger(amount), ByteUtils.copyOf(txHash.getBytes(), 8), (byte) -1);
        txSize += from.size();
        BigInteger fee = TransactionFeeCalculator.getNormalTxFee(txSize);
        String fromAmount = BigIntegerUtils.addToString(amount, fee.toString());
        if (BigIntegerUtils.isLessThan(accountBalance.getAvailable(), fromAmount)) {
            throw new NulsRuntimeException(CmErrorCode.BALANCE_NOT_ENOUGH);
        }
        from.setAmount(new BigInteger(fromAmount));
        coinData.addFrom(from);
        return coinData;
    }

    BlockChain buildChainWithTxData(String txHex, Transaction tx, boolean isDelete) {
        try {
            byte[] txBytes = RPCUtil.decode(txHex);
            tx.parse(txBytes, 0);
            TxChain txChain = new TxChain();
            txChain.parse(tx.getTxData(), 0);
            BlockChain blockChain = new BlockChain(txChain);
            if (isDelete) {
                blockChain.setDelTxHash(tx.getHash().toHex());
                blockChain.setRegAddress(txChain.getAddress());
                blockChain.setRegAssetId(txChain.getAssetId());
            } else {
                blockChain.setRegTxHash(tx.getHash().toHex());
                blockChain.setDelAddress(txChain.getAddress());
                blockChain.setDelAssetId(txChain.getAssetId());
            }
            return blockChain;
        } catch (Exception e) {
            LoggerUtil.logger().error(e);
            return null;
        }
    }

    Asset buildAssetWithTxChain(String txHex, Transaction tx) {
        try {
            byte[] txBytes = RPCUtil.decode(txHex);
            tx.parse(txBytes, 0);
            TxChain txChain = new TxChain();
            txChain.parse(tx.getTxData(), 0);
            Asset asset = new Asset(txChain);
            asset.setTxHash(tx.getHash().toHex());
            return asset;
        } catch (Exception e) {
            LoggerUtil.logger().error(e);
            return null;
        }
    }

//    protected Asset buildAssetWithTxAsset(String txHex, Transaction tx) {
//        try {
//            byte[] txBytes = HexUtil.hexToByte(txHex);
//            tx.parse(txBytes, 0);
//            TxAsset txAsset = new TxAsset();
//            txAsset.parse(tx.getTxData(), 0);
//            Asset asset = new Asset();
//            asset.setTxHash(tx.getHash().toString());
//            return asset;
//        } catch (Exception e) {
//            Log.error(e);
//            return null;
//        }
//    }

    Asset setDefaultAssetValue(Asset asset) {

        return asset;
    }
}