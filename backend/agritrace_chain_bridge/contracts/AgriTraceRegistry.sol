// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract AgriTraceRegistry {
    address public owner;
    mapping(bytes32 => bytes32) private batchHashes;

    event BatchCreated(string batchCode, bytes32 payloadHash, uint256 timestamp);

    modifier onlyOwner() {
        require(msg.sender == owner, "ONLY_OWNER");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function recordBatchCreated(string calldata batchCode, bytes32 payloadHash) external onlyOwner {
        bytes32 key = keccak256(bytes(batchCode));
        batchHashes[key] = payloadHash;
        emit BatchCreated(batchCode, payloadHash, block.timestamp);
    }

    function getBatchHash(string calldata batchCode) external view returns (bytes32) {
        bytes32 key = keccak256(bytes(batchCode));
        return batchHashes[key];
    }
}
